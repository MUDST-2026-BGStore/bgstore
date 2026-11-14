package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.contracts.model.CardBrand;
import com.chanakanlabs.theparlor.contracts.model.CustomerReference;
import com.chanakanlabs.theparlor.contracts.model.GuestCustomer;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.OrderPayment;
import com.chanakanlabs.theparlor.contracts.model.RegisteredCustomer;
import com.chanakanlabs.theparlor.contracts.model.Reservation;
import com.chanakanlabs.theparlor.contracts.model.ReservationPreOrder;
import com.chanakanlabs.theparlor.contracts.model.ReservationStatus;
import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationGame;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationGameRepository;
import com.chanakanlabs.theparlor.reservations.spi.RentalDesk;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Builds reservation responses with a fixed number of reads per page, whatever its size. */
@Component
class ReservationViews {
  private final ReservationGameRepository games;
  private final PreOrderDesk preOrders;
  private final Payments payments;
  private final RentalDesk rentalDesk;

  // Lazy: the rental desk needs reservations to describe a rental, and reservations need the
  // desk for a rental's ID. Neither is used while the other is being constructed.
  ReservationViews(
      ReservationGameRepository games,
      PreOrderDesk preOrders,
      Payments payments,
      @Lazy RentalDesk rentalDesk) {
    this.games = games;
    this.preOrders = preOrders;
    this.payments = payments;
    this.rentalDesk = rentalDesk;
  }

  Reservation of(com.chanakanlabs.theparlor.reservations.internal.domain.Reservation one) {
    return of(List.of(one)).getFirst();
  }

  List<Reservation> of(
      List<com.chanakanlabs.theparlor.reservations.internal.domain.Reservation> page) {
    if (page.isEmpty()) return List.of();
    var ids = page.stream().map(reservation -> reservation.getId()).toList();
    Map<UUID, List<ReservationGame>> selected = gamesOf(ids);
    Map<UUID, UUID> rentals = rentalDesk.rentalIds(ids);
    Map<UUID, ReservationPreOrder> food = preOrders.views(ids);
    var paid = payments.of(Payments.Kind.RESERVATION, ids);
    return page.stream()
        .map(
            reservation -> {
              var result =
                  view(
                      reservation,
                      selected.getOrDefault(reservation.getId(), List.of()),
                      rentals.get(reservation.getId()));
              result.setPreOrder(food.get(reservation.getId()));
              var summary = paid.get(reservation.getId());
              if (summary != null) {
                var payment =
                    new OrderPayment(
                        OrderPayment.MethodEnum.valueOf(summary.method().name()),
                        OrderPayment.StatusEnum.valueOf(summary.status().name()),
                        new Money(summary.amountMinor()));
                if (summary.cardBrand() != null)
                  payment.setCardBrand(CardBrand.valueOf(summary.cardBrand()));
                payment.setCardLast4(summary.cardLast4());
                payment.setPromptPayQr(summary.promptPayQr());
                if (summary.expiresAt() != null)
                  payment.setExpiresAt(summary.expiresAt().atOffset(ZoneOffset.UTC));
                result.setPayment(payment);
              }
              return result;
            })
        .toList();
  }

  Map<UUID, List<ReservationGame>> gamesOf(List<UUID> reservationIds) {
    return games.findByIdReservationIdIn(reservationIds).stream()
        .sorted(Comparator.comparing(game -> game.getId().gameId()))
        .collect(Collectors.groupingBy(game -> game.getId().reservationId()));
  }

  static CustomerReference customer(
      com.chanakanlabs.theparlor.reservations.internal.domain.Reservation reservation) {
    if (reservation.getCustomerSubject() != null)
      return new RegisteredCustomer(
          RegisteredCustomer.KindEnum.REGISTERED, reservation.getCustomerSubject());
    var guest = new GuestCustomer(GuestCustomer.KindEnum.GUEST, reservation.getGuestName());
    guest.setPhone(reservation.getGuestPhone());
    return guest;
  }

  private static Reservation view(
      com.chanakanlabs.theparlor.reservations.internal.domain.Reservation reservation,
      List<ReservationGame> selected,
      UUID rentalId) {
    var result =
        new Reservation(
            reservation.getId(),
            reservation.getBranchId(),
            reservation.getTableId(),
            customer(reservation),
            Reservation.SourceEnum.valueOf(reservation.getSource().name()),
            ReservationStatus.valueOf(reservation.getStatus().name()),
            reservation.getStartsAt().atOffset(ZoneOffset.UTC),
            reservation.getEndsAt().atOffset(ZoneOffset.UTC),
            reservation.getPartySize(),
            selected.stream()
                .map(
                    game ->
                        new com.chanakanlabs.theparlor.contracts.model.ReservationGame(
                            game.getId().gameId(),
                            game.getQuantity(),
                            new Money(game.getUnitHourlyRateMinor())))
                .toList(),
            new Money(reservation.getTableHourlyRateMinor()),
            reservation.getCreatedAt().atOffset(ZoneOffset.UTC),
            reservation.getCreatedBySubject());
    result.setNotes(reservation.getNotes());
    if (reservation.getCheckedInAt() != null) {
      result.setCheckedInAt(reservation.getCheckedInAt().atOffset(ZoneOffset.UTC));
      result.setCheckedInBySubject(reservation.getCheckedInBy());
      result.setRentalId(rentalId);
    }
    if (reservation.getCompletedAt() != null)
      result.setCompletedAt(reservation.getCompletedAt().atOffset(ZoneOffset.UTC));
    if (reservation.getCancelledAt() != null) {
      result.setCancelledAt(reservation.getCancelledAt().atOffset(ZoneOffset.UTC));
      result.setCancelledBySubject(reservation.getCancelledBy());
      result.setCancellationReason(reservation.getCancellationReason());
    }
    if (reservation.getNoShowAt() != null) {
      result.setNoShowAt(reservation.getNoShowAt().atOffset(ZoneOffset.UTC));
      result.setNoShowBySubject(reservation.getNoShowBy());
      result.setNoShowReason(reservation.getNoShowReason());
    }
    return result;
  }
}
