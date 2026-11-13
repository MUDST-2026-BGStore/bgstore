package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.contracts.model.CardBrand;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.OrderPayment;
import com.chanakanlabs.theparlor.contracts.model.PreOrderRequest;
import com.chanakanlabs.theparlor.contracts.model.ReservationPreOrder;
import com.chanakanlabs.theparlor.contracts.model.ReservationPreOrderItem;
import com.chanakanlabs.theparlor.pantry.Pantry;
import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.payments.Payments.Kind;
import com.chanakanlabs.theparlor.payments.Payments.Payable;
import com.chanakanlabs.theparlor.payments.Payments.PaymentSummary;
import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationPreOrderItemRepository;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationPreOrderRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A reservation's food pre-order: held at the branch and authorized on the card at booking, served
 * and captured at check-in, released and voided when the visit is cancelled or missed. Every method
 * runs inside the reservation's transaction, after the reservation row is locked.
 */
@Component
class PreOrderDesk {
  private final ReservationPreOrderRepository preOrders;
  private final ReservationPreOrderItemRepository items;
  private final Pantry pantry;
  private final Payments payments;

  PreOrderDesk(
      ReservationPreOrderRepository preOrders,
      ReservationPreOrderItemRepository items,
      Pantry pantry,
      Payments payments) {
    this.preOrders = preOrders;
    this.items = items;
    this.pantry = pantry;
    this.payments = payments;
  }

  /**
   * Holds the stock, records the priced lines and authorizes the total. The card is charged last,
   * so a refused stock hold never touches it and a decline rolls back the whole booking.
   */
  void place(Reservation reservation, PreOrderRequest request, Instant now) {
    if (request.getItems() == null || request.getItems().stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Pre-order items are required");
    requireWindow(reservation.getStartsAt(), now);
    var priced =
        pantry.hold(
            reservation.getBranchId(),
            request.getItems().stream()
                .map(item -> new Pantry.Selection(item.getMenuItemId(), item.getQuantity()))
                .toList());
    var lines =
        priced.stream()
            .map(
                item ->
                    com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrderItem
                        .line(
                            reservation.getId(),
                            item.menuItemId(),
                            item.name(),
                            item.quantity(),
                            item.unitPriceMinor()))
            .toList();
    var preOrder =
        com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrder.price(
            reservation.getId(), lines, now);
    preOrders.saveAndFlush(preOrder);
    items.saveAllAndFlush(lines);
    payments.authorizeCard(
        Payable.reservation(reservation.getId()),
        preOrder.getTotalMinor(),
        request.getPaymentToken());
  }

  /** Rejects moving a pre-ordered visit beyond what the card authorization can cover. */
  void requireReschedulable(Reservation reservation, Instant newStartsAt) {
    preOrders
        .findById(reservation.getId())
        .ifPresent(preOrder -> requireWindow(newStartsAt, preOrder.getCreatedAt()));
  }

  /**
   * The visit will not happen: return the stock and release the card authorization. The void cannot
   * be rolled back, so call this after the reservation change has been flushed.
   */
  void release(Reservation reservation) {
    var selections = selections(reservation.getId());
    if (selections.isEmpty()) return;
    pantry.release(reservation.getBranchId(), selections);
    payments.cancel(Payable.reservation(reservation.getId()));
  }

  /** The party arrived: the food is served from held stock. Capture the money with capture(). */
  void serve(Reservation reservation) {
    var selections = selections(reservation.getId());
    if (selections.isEmpty()) return;
    pantry.fulfill(reservation.getBranchId(), selections);
  }

  /**
   * Takes the pre-order money. A gateway capture cannot be rolled back with the database, so call
   * this after every other check-in step that can fail.
   */
  void capture(Reservation reservation) {
    if (preOrders.existsById(reservation.getId()))
      payments.capture(Payable.reservation(reservation.getId()));
  }

  /** Pre-order views by reservation ID, with three reads for the whole page. */
  Map<UUID, ReservationPreOrder> views(List<UUID> reservationIds) {
    var found = preOrders.findByReservationIdIn(reservationIds);
    if (found.isEmpty()) return Map.of();
    var ids = found.stream().map(preOrder -> preOrder.getReservationId()).toList();
    var lines =
        items.findByIdReservationIdIn(ids).stream()
            .collect(Collectors.groupingBy(line -> line.getId().reservationId()));
    var paid = payments.of(Kind.RESERVATION, ids);
    return found.stream()
        .collect(
            Collectors.toMap(
                preOrder -> preOrder.getReservationId(),
                preOrder ->
                    view(
                        preOrder,
                        lines.getOrDefault(preOrder.getReservationId(), List.of()),
                        paid.get(preOrder.getReservationId()))));
  }

  private List<Pantry.Selection> selections(UUID reservationId) {
    return items.findByIdReservationIdIn(List.of(reservationId)).stream()
        .map(line -> new Pantry.Selection(line.getId().menuItemId(), line.getQuantity()))
        .toList();
  }

  private static void requireWindow(Instant startsAt, Instant now) {
    if (!com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrder.allowedFor(
        startsAt, now))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Food can be pre-ordered only for visits starting within "
              + com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrder.WINDOW
                  .toDays()
              + " days");
  }

  private static ReservationPreOrder view(
      com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrder preOrder,
      List<com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrderItem> lines,
      PaymentSummary payment) {
    if (payment == null)
      throw new IllegalStateException(
          "Pre-order of reservation " + preOrder.getReservationId() + " has no payment");
    var summary =
        new OrderPayment(
            OrderPayment.MethodEnum.valueOf(payment.method().name()),
            OrderPayment.StatusEnum.valueOf(payment.status().name()),
            new Money(payment.amountMinor()));
    if (payment.cardBrand() != null) summary.setCardBrand(CardBrand.valueOf(payment.cardBrand()));
    summary.setCardLast4(payment.cardLast4());
    return new ReservationPreOrder(
        lines.stream()
            .sorted(Comparator.comparing(line -> line.getId().menuItemId()))
            .map(
                line ->
                    new ReservationPreOrderItem(
                        line.getId().menuItemId(),
                        line.getName(),
                        line.getQuantity(),
                        new Money(line.getUnitPriceMinor()),
                        new Money(line.getLineTotalMinor())))
            .toList(),
        new Money(preOrder.getSubtotalMinor()),
        new Money(preOrder.getDiscountMinor()),
        new Money(preOrder.getTotalMinor()),
        summary);
  }
}
