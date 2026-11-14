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
 * A reservation's food stock and payment lifecycle. Cards authorize food at booking and capture at
 * check-in; PromptPay collects booked play and food upfront. Unpaid cancellations return stock and
 * void payment. Received QR payments require refund handling before cancellation. Methods run in
 * the reservation's transaction, after the reservation row is locked.
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
  void place(Reservation reservation, PreOrderRequest request, Instant now, boolean payByCard) {
    if (payByCard && (request.getPaymentToken() == null || request.getPaymentToken().isBlank()))
      throw new IllegalArgumentException("A card pre-order requires a payment token");
    if (!payByCard && request.getPaymentToken() != null)
      throw new IllegalArgumentException("PromptPay does not accept a card token");
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
    if (payByCard)
      payments.authorizeCard(
          Payable.reservation(reservation.getId()),
          preOrder.getTotalMinor(),
          request.getPaymentToken());
  }

  /** Rejects moving a pre-ordered visit beyond what the card authorization can cover. */
  void requireReschedulable(Reservation reservation, Instant newStartsAt) {
    var payment =
        payments.of(Kind.RESERVATION, List.of(reservation.getId())).get(reservation.getId());
    if (payment != null && payment.method() == Payments.Method.PROMPTPAY)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "A PromptPay reservation cannot be rescheduled; unpaid bookings can be cancelled and rebooked");
    preOrders
        .findById(reservation.getId())
        .ifPresent(preOrder -> requireWindow(newStartsAt, preOrder.getCreatedAt()));
  }

  /**
   * The visit will not happen: return the stock and release the card authorization. The void cannot
   * be rolled back, so call this after the reservation change has been flushed.
   */
  void release(Reservation reservation) {
    releaseStock(reservation);
    if (payments
        .of(Kind.RESERVATION, List.of(reservation.getId()))
        .containsKey(reservation.getId()))
      payments.cancel(Payable.reservation(reservation.getId()));
  }

  /**
   * Records an absent party without reversing money already received. Unserved food returns to
   * stock. Card holds and unpaid QRs are voided; a paid QR remains CAPTURED, independently of any
   * future refund or forfeiture decision. Refresh pending QRs to account for delayed webhooks.
   */
  void releaseForNoShow(Reservation reservation) {
    releaseStock(reservation);
    var payment =
        payments.of(Kind.RESERVATION, List.of(reservation.getId())).get(reservation.getId());
    if (payment == null) return;
    var payable = Payable.reservation(reservation.getId());
    if (payment.method() == Payments.Method.PROMPTPAY && payments.refreshPromptPay(payable)) return;
    payments.cancel(payable);
  }

  private void releaseStock(Reservation reservation) {
    var selections = selections(reservation.getId());
    if (!selections.isEmpty()) pantry.release(reservation.getBranchId(), selections);
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

  /** The QR includes all booked play and any discounted food, using server prices only. */
  void startPromptPay(Reservation reservation, long playMinor) {
    long foodMinor =
        preOrders
            .findById(reservation.getId())
            .map(preOrder -> preOrder.getTotalMinor())
            .orElse(0L);
    long totalMinor = Math.addExact(playMinor, foodMinor);
    if (totalMinor <= 0)
      throw new IllegalArgumentException("PromptPay requires a positive booking total");
    payments.startPromptPay(Payable.reservation(reservation.getId()), totalMinor);
  }

  /** Reject unpaid arrivals before stock is served or a rental starts. */
  void requirePaid(Reservation reservation) {
    var payment =
        payments.of(Kind.RESERVATION, List.of(reservation.getId())).get(reservation.getId());
    if (payment != null
        && payment.method() == Payments.Method.PROMPTPAY
        && !payments.refreshPromptPay(Payable.reservation(reservation.getId())))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "PromptPay payment has not been received");
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
    summary.setPromptPayQr(payment.promptPayQr());
    if (payment.expiresAt() != null)
      summary.setExpiresAt(payment.expiresAt().atOffset(java.time.ZoneOffset.UTC));
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
