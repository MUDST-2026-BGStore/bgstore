package com.chanakanlabs.theparlor.orders.internal.application;

import com.chanakanlabs.theparlor.contracts.model.CardBrand;
import com.chanakanlabs.theparlor.contracts.model.CustomerReference;
import com.chanakanlabs.theparlor.contracts.model.GuestCustomer;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.Order;
import com.chanakanlabs.theparlor.contracts.model.OrderContact;
import com.chanakanlabs.theparlor.contracts.model.OrderPayment;
import com.chanakanlabs.theparlor.contracts.model.OrderStatus;
import com.chanakanlabs.theparlor.contracts.model.RegisteredCustomer;
import com.chanakanlabs.theparlor.contracts.model.ShippingAddress;
import com.chanakanlabs.theparlor.orders.internal.domain.OrderItem;
import com.chanakanlabs.theparlor.orders.internal.persistence.OrderItemRepository;
import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.payments.Payments.PaymentSummary;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds order responses with one items read and one payments read per page. */
@Component
class OrderViews {
  private final OrderItemRepository items;
  private final Payments payments;

  OrderViews(OrderItemRepository items, Payments payments) {
    this.items = items;
    this.payments = payments;
  }

  Order of(com.chanakanlabs.theparlor.orders.internal.domain.Order order) {
    return of(List.of(order)).getFirst();
  }

  List<Order> of(List<com.chanakanlabs.theparlor.orders.internal.domain.Order> orders) {
    if (orders.isEmpty()) return List.of();
    var ids = orders.stream().map(order -> order.getId()).toList();
    Map<UUID, List<OrderItem>> lines =
        items.findByIdOrderIdIn(ids).stream()
            .collect(Collectors.groupingBy(item -> item.getId().orderId()));
    Map<UUID, PaymentSummary> paid = payments.of(Payments.Kind.ORDER, ids);
    return orders.stream()
        .map(order -> view(order, lines.getOrDefault(order.getId(), List.of()), paid))
        .toList();
  }

  private static Order view(
      com.chanakanlabs.theparlor.orders.internal.domain.Order order,
      List<OrderItem> lines,
      Map<UUID, PaymentSummary> paid) {
    var contact = new OrderContact(order.getContactName());
    contact.setEmail(order.getContactEmail());
    contact.setPhone(order.getContactPhone());
    var result =
        new Order(
            order.getId(),
            order.getBranchId(),
            customer(order),
            Order.SourceEnum.valueOf(order.getSource().name()),
            OrderStatus.valueOf(order.getStatus().name()),
            lines.stream()
                .sorted(Comparator.comparing(item -> item.getId().editionId()))
                .map(
                    item ->
                        new com.chanakanlabs.theparlor.contracts.model.OrderItem(
                                item.getId().editionId(),
                                item.getGameName(),
                                item.getEditionLanguage(),
                                item.getQuantity(),
                                new Money(item.getUnitPriceMinor()),
                                new Money(item.getLineTotalMinor()))
                            .gameId(item.getGameId()))
                .toList(),
            new Money(order.getTotalMinor()),
            contact,
            Order.FulfillmentMethodEnum.valueOf(order.getFulfillmentMethod().name()),
            payment(paid.get(order.getId()), order),
            order.getCreatedAt().atOffset(ZoneOffset.UTC),
            order.getCreatedBySubject());
    result.setShippingAddress(address(order.getShippingAddress()));
    result.setNotes(order.getNotes());
    if (order.getFulfilledAt() != null) {
      result.setFulfilledAt(order.getFulfilledAt().atOffset(ZoneOffset.UTC));
      result.setFulfilledBySubject(order.getFulfilledBy());
    }
    if (order.getCancelledAt() != null) {
      result.setCancelledAt(order.getCancelledAt().atOffset(ZoneOffset.UTC));
      result.setCancelledBySubject(order.getCancelledBy());
      result.setCancellationReason(order.getCancellationReason());
    }
    return result;
  }

  private static CustomerReference customer(
      com.chanakanlabs.theparlor.orders.internal.domain.Order order) {
    if (order.getCustomerSubject() != null)
      return new RegisteredCustomer(
          RegisteredCustomer.KindEnum.REGISTERED, order.getCustomerSubject());
    var guest = new GuestCustomer(GuestCustomer.KindEnum.GUEST, order.getContactName());
    guest.setPhone(order.getContactPhone());
    return guest;
  }

  private static OrderPayment payment(
      PaymentSummary summary, com.chanakanlabs.theparlor.orders.internal.domain.Order order) {
    if (summary == null)
      throw new IllegalStateException("Order " + order.getId() + " has no payment");
    var result =
        new OrderPayment(
            OrderPayment.MethodEnum.valueOf(summary.method().name()),
            OrderPayment.StatusEnum.valueOf(summary.status().name()),
            new Money(summary.amountMinor()));
    if (summary.cardBrand() != null) result.setCardBrand(CardBrand.valueOf(summary.cardBrand()));
    result.setCardLast4(summary.cardLast4());
    return result;
  }

  static Map<String, Object> address(ShippingAddress address) {
    if (address == null) return null;
    var map = new LinkedHashMap<String, Object>();
    map.put("recipientName", address.getRecipientName());
    map.put("line1", address.getLine1());
    if (address.getLine2() != null) map.put("line2", address.getLine2());
    if (address.getSubdistrict() != null) map.put("subdistrict", address.getSubdistrict());
    map.put("district", address.getDistrict());
    map.put("province", address.getProvince());
    map.put("postalCode", address.getPostalCode());
    return map;
  }

  private static ShippingAddress address(Map<String, Object> stored) {
    if (stored == null) return null;
    var address =
        new ShippingAddress(
            (String) stored.get("recipientName"),
            (String) stored.get("line1"),
            (String) stored.get("district"),
            (String) stored.get("province"),
            (String) stored.get("postalCode"));
    address.setLine2((String) stored.get("line2"));
    address.setSubdistrict((String) stored.get("subdistrict"));
    return address;
  }
}
