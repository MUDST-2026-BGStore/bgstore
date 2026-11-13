package com.chanakanlabs.theparlor.orders.internal.application;

import com.chanakanlabs.theparlor.account.AccountDirectory;
import com.chanakanlabs.theparlor.account.AccountNotProvisionedException;
import com.chanakanlabs.theparlor.account.CurrentAccount;
import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.contracts.model.CreateOrderRequest;
import com.chanakanlabs.theparlor.contracts.model.CreateStaffOrderRequest;
import com.chanakanlabs.theparlor.contracts.model.GuestCustomer;
import com.chanakanlabs.theparlor.contracts.model.Order;
import com.chanakanlabs.theparlor.contracts.model.OrderItemRequest;
import com.chanakanlabs.theparlor.contracts.model.OrderPage;
import com.chanakanlabs.theparlor.contracts.model.OrderStatus;
import com.chanakanlabs.theparlor.contracts.model.RegisteredCustomer;
import com.chanakanlabs.theparlor.customers.Customers;
import com.chanakanlabs.theparlor.idempotency.Idempotency;
import com.chanakanlabs.theparlor.idempotency.Idempotency.Scope;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.identity.UserProfile;
import com.chanakanlabs.theparlor.inventory.SaleInventory;
import com.chanakanlabs.theparlor.orders.OrderPlacement.Line;
import com.chanakanlabs.theparlor.orders.OrderPlacement.Placement;
import com.chanakanlabs.theparlor.orders.internal.domain.Order.Buyer;
import com.chanakanlabs.theparlor.orders.internal.persistence.OrderItemRepository;
import com.chanakanlabs.theparlor.orders.internal.persistence.OrderRepository;
import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.payments.Payments.Payable;
import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Sale orders: customer and counter placement, reads, cancellation and staff fulfillment. */
@Service
@Transactional(readOnly = true)
public class OrdersService {
  private final OrderPlacementService placement;
  private final OrderViews views;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final SaleInventory inventory;
  private final Payments payments;
  private final BranchAccess branches;
  private final CurrentUser currentUser;
  private final CurrentAccount currentAccount;
  private final AccountDirectory accounts;
  private final Customers customers;
  private final Idempotency idempotency;
  private final Clock clock;

  OrdersService(
      OrderPlacementService placement,
      OrderViews views,
      OrderRepository orders,
      OrderItemRepository items,
      SaleInventory inventory,
      Payments payments,
      BranchAccess branches,
      CurrentUser currentUser,
      CurrentAccount currentAccount,
      AccountDirectory accounts,
      Customers customers,
      Idempotency idempotency,
      Clock clock) {
    this.placement = placement;
    this.views = views;
    this.orders = orders;
    this.items = items;
    this.inventory = inventory;
    this.payments = payments;
    this.branches = branches;
    this.currentUser = currentUser;
    this.currentAccount = currentAccount;
    this.accounts = accounts;
    this.customers = customers;
    this.idempotency = idempotency;
    this.clock = clock;
  }

  /** A signed-in customer buys for pickup at this branch, paying by card. */
  @Transactional
  public Order createMyOrder(UUID branchId, UUID idempotencyKey, CreateOrderRequest request) {
    var user = currentUser.require();
    var profile =
        currentAccount.findCurrentProfile().orElseThrow(AccountNotProvisionedException::new);
    var scope = new Scope(user.subject(), "POST", "/api/v1/branches/" + branchId + "/orders");
    UUID orderId =
        idempotency.once(
            scope,
            idempotencyKey,
            request.toString(),
            () ->
                placement
                    .place(
                        new Placement(
                            branchId,
                            profile.getId(),
                            user.subject(),
                            (profile.getFirstName() + " " + profile.getLastName()).strip(),
                            user.email(),
                            profile.getPhoneNumber(),
                            lines(request.getItems()),
                            null,
                            request.getNotes(),
                            request.getPaymentToken()))
                    .getId());
    return placement.get(orderId);
  }

  /** Staff record a counter sale for a registered customer or a walk-in guest. */
  @Transactional
  public Order createStaffOrder(
      UUID branchId, UUID idempotencyKey, CreateStaffOrderRequest request) {
    String staff = branches.requireOperator(branchId);
    var scope = new Scope(staff, "POST", "/api/v1/branches/" + branchId + "/staff/orders");
    UUID orderId =
        idempotency.once(
            scope,
            idempotencyKey,
            request.toString(),
            () ->
                placement
                    .placeAtCounter(
                        branchId,
                        buyer(request),
                        lines(request.getItems()),
                        request.getNotes(),
                        staff)
                    .getId());
    return placement.get(orderId);
  }

  public Order getOrder(UUID branchId, UUID orderId) {
    var order = orders.findByIdAndBranchId(orderId, branchId).orElseThrow(OrdersService::missing);
    requireOwnerOrOperator(order);
    return views.of(order);
  }

  public OrderPage listBranchOrders(
      UUID branchId, Integer limit, @Nullable String cursor, @Nullable OrderStatus status) {
    branches.requireOperator(branchId);
    return page("branch-orders", "branchId", branchId, limit, cursor, status);
  }

  public OrderPage listMyOrders(
      Integer limit, @Nullable String cursor, @Nullable OrderStatus status) {
    return page(
        "my-orders", "customerSubject", currentUser.require().subject(), limit, cursor, status);
  }

  /** PLACED to CANCELLED: releases the stock hold and voids the payment. */
  @Transactional
  public Order cancelOrder(UUID branchId, UUID orderId, String reason) {
    var order = orders.lockByIdAndBranchId(orderId, branchId).orElseThrow(OrdersService::missing);
    String actor = requireOwnerOrOperator(order);
    try {
      if (order.cancel(actor, reason, clock.instant())) {
        inventory.release(branchId, selections(orderId));
        payments.cancel(Payable.order(orderId));
        orders.flush();
      }
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
    return views.of(order);
  }

  /** PLACED to FULFILLED: consumes the held stock and captures the payment. */
  @Transactional
  public Order fulfillOrder(UUID branchId, UUID orderId) {
    String staff = branches.requireOperator(branchId);
    var order = orders.lockByIdAndBranchId(orderId, branchId).orElseThrow(OrdersService::missing);
    try {
      if (order.fulfill(staff, clock.instant())) {
        inventory.fulfill(branchId, selections(orderId));
        payments.capture(Payable.order(orderId));
        orders.flush();
      }
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
    return views.of(order);
  }

  /**
   * Returns the caller's subject. The customer an order belongs to may always see and cancel it;
   * anyone else must operate the branch. Callers who are neither learn nothing: staff get the usual
   * 403, customers get the same 404 as for an order that does not exist.
   */
  private String requireOwnerOrOperator(
      com.chanakanlabs.theparlor.orders.internal.domain.Order order) {
    UserProfile user = currentUser.require();
    if (user.subject().equals(order.getCustomerSubject())) return user.subject();
    if (user.roles().stream().noneMatch(List.of("staff", "manager", "admin")::contains))
      throw missing();
    return branches.requireOperator(order.getBranchId());
  }

  private Buyer buyer(CreateStaffOrderRequest request) {
    if (request.getCustomer() instanceof RegisteredCustomer registered) {
      var account =
          accounts
              .findBySubject(registered.getSubject())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
      return new Buyer(
          customers.forAccount(account.accountId()),
          registered.getSubject(),
          account.name(),
          null,
          account.phoneNumber());
    }
    if (request.getCustomer() instanceof GuestCustomer guest)
      return new Buyer(null, null, guest.getDisplayName(), null, guest.getPhone());
    throw new IllegalArgumentException("Customer is required");
  }

  private OrderPage page(
      String operation,
      String ownerField,
      Object owner,
      Integer requestedLimit,
      String cursor,
      OrderStatus status) {
    int limit = KeysetCursor.limit(requestedLimit);
    String filters = owner + "|" + (status == null ? "" : status.name());
    UUID after = KeysetCursor.afterId(cursor, operation, filters);
    Specification<com.chanakanlabs.theparlor.orders.internal.domain.Order> matching =
        (root, query, cb) -> {
          var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
          predicates.add(cb.equal(root.get(ownerField), owner));
          if (status != null)
            predicates.add(
                cb.equal(
                    root.get("status"),
                    com.chanakanlabs.theparlor.orders.internal.domain.OrderStatus.valueOf(
                        status.name())));
          if (after != null) predicates.add(cb.greaterThan(root.get("id"), after));
          return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var rows = orders.findBy(matching, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var page = rows.stream().limit(limit).toList();
    var result = new OrderPage(views.of(page));
    if (rows.size() > limit)
      result.setNextCursor(KeysetCursor.next(operation, filters, page.getLast().getId()));
    return result;
  }

  private List<SaleInventory.Selection> selections(UUID orderId) {
    return items.findByIdOrderIdIn(List.of(orderId)).stream()
        .map(item -> new SaleInventory.Selection(item.getId().editionId(), item.getQuantity()))
        .toList();
  }

  private static List<Line> lines(List<OrderItemRequest> requested) {
    if (requested == null || requested.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Order lines are required");
    return requested.stream()
        .map(line -> new Line(line.getEditionId(), line.getQuantity()))
        .toList();
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
  }
}
