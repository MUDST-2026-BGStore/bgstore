package com.chanakanlabs.theparlor.orders.internal.application;

import com.chanakanlabs.theparlor.contracts.model.Order;
import com.chanakanlabs.theparlor.contracts.model.ShippingAddress;
import com.chanakanlabs.theparlor.customers.Customers;
import com.chanakanlabs.theparlor.inventory.SaleInventory;
import com.chanakanlabs.theparlor.orders.OrderPlacement;
import com.chanakanlabs.theparlor.orders.internal.domain.Order.Buyer;
import com.chanakanlabs.theparlor.orders.internal.domain.OrderItem;
import com.chanakanlabs.theparlor.orders.internal.domain.OrderSource;
import com.chanakanlabs.theparlor.orders.internal.persistence.OrderItemRepository;
import com.chanakanlabs.theparlor.orders.internal.persistence.OrderRepository;
import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.payments.Payments.Payable;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The one place an order comes into existence: hold stock, snapshot prices, write the order and
 * settle how it is paid, all in the caller's transaction.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class OrderPlacementService implements OrderPlacement {
  /** Recorded as the creator of an order placed without a signed-in session. */
  private static final String GUEST_SUBJECT = "guest";

  private final SaleInventory inventory;
  private final Customers customers;
  private final Payments payments;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final OrderViews views;

  OrderPlacementService(
      SaleInventory inventory,
      Customers customers,
      Payments payments,
      OrderRepository orders,
      OrderItemRepository items,
      OrderViews views) {
    this.inventory = inventory;
    this.customers = customers;
    this.payments = payments;
    this.orders = orders;
    this.items = items;
    this.views = views;
  }

  @Override
  public Order place(Placement placement) {
    var buyer =
        new Buyer(
            placement.accountId() == null ? null : customers.forAccount(placement.accountId()),
            placement.subject(),
            placement.contactName(),
            placement.contactEmail(),
            placement.contactPhone());
    if (buyer.email() == null || buyer.phone() == null)
      throw new IllegalArgumentException("Online orders need a contact email and phone");
    var order =
        write(
            placement.branchId(),
            buyer,
            OrderSource.CUSTOMER,
            placement.lines(),
            placement.shippingAddress(),
            placement.notes(),
            placement.subject() == null ? GUEST_SUBJECT : placement.subject());
    payments.authorizeCard(
        Payable.order(order.getId()), order.getTotalMinor(), placement.paymentToken());
    return views.of(order);
  }

  /** A counter sale: staff take payment themselves, so no gateway is involved. */
  Order placeAtCounter(
      UUID branchId, Buyer buyer, List<Line> lines, String notes, String staffSubject) {
    var order = write(branchId, buyer, OrderSource.STAFF_COUNTER, lines, null, notes, staffSubject);
    payments.settleAtCounter(Payable.order(order.getId()), order.getTotalMinor());
    return views.of(order);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Order get(UUID orderId) {
    return views.of(
        orders
            .findById(orderId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
  }

  private com.chanakanlabs.theparlor.orders.internal.domain.Order write(
      UUID branchId,
      Buyer buyer,
      OrderSource source,
      List<Line> lines,
      ShippingAddress shippingAddress,
      String notes,
      String createdBySubject) {
    if (lines == null || lines.isEmpty())
      throw new IllegalArgumentException("An order needs at least one line");
    // All or nothing: every quantity is held at the branch's current price, or this throws.
    var priced =
        inventory.hold(
            branchId,
            lines.stream()
                .map(line -> new SaleInventory.Selection(line.editionId(), line.quantity()))
                .toList());
    long total = 0;
    for (var line : priced)
      total = Math.addExact(total, Math.multiplyExact(line.unitPriceMinor(), line.quantity()));
    var order =
        orders.saveAndFlush(
            com.chanakanlabs.theparlor.orders.internal.domain.Order.place(
                branchId,
                buyer,
                source,
                OrderViews.address(shippingAddress),
                notes,
                total,
                createdBySubject));
    items.saveAllAndFlush(
        priced.stream()
            .map(
                line ->
                    OrderItem.snapshot(
                        order.getId(),
                        line.editionId(),
                        line.gameId(),
                        line.gameName(),
                        line.editionLanguage(),
                        line.quantity(),
                        line.unitPriceMinor()))
            .toList());
    return order;
  }
}
