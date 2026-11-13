package com.chanakanlabs.theparlor.reservations.internal.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationPreOrderTests {
  static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");
  final UUID reservationId = UUID.randomUUID();

  ReservationPreOrderItem line(long unitPriceMinor, int quantity) {
    return ReservationPreOrderItem.line(
        reservationId, UUID.randomUUID(), "Item", quantity, unitPriceMinor);
  }

  @Test
  void takesTenPercentOffTheSubtotal() {
    var preOrder =
        ReservationPreOrder.price(reservationId, List.of(line(3500, 2), line(2500, 1)), NOW);
    assertThat(preOrder.getSubtotalMinor()).isEqualTo(9500);
    assertThat(preOrder.getDiscountMinor()).isEqualTo(950);
    assertThat(preOrder.getTotalMinor()).isEqualTo(8550);
    assertThat(preOrder.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void roundsTheDiscountHalfUpToWholeSatang() {
    assertThat(ReservationPreOrder.discount(5)).isEqualTo(1);
    assertThat(ReservationPreOrder.discount(4)).isZero();
    assertThat(ReservationPreOrder.discount(15)).isEqualTo(2);
    assertThat(ReservationPreOrder.discount(14)).isEqualTo(1);
  }

  @Test
  void refusesEmptyFreeOrForeignLines() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ReservationPreOrder.price(reservationId, List.of(), NOW));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ReservationPreOrder.price(reservationId, List.of(line(0, 1)), NOW));
    var foreign = ReservationPreOrderItem.line(UUID.randomUUID(), UUID.randomUUID(), "X", 1, 100);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ReservationPreOrder.price(reservationId, List.of(foreign), NOW));
  }

  @Test
  void lineTotalsAreUnitPriceTimesQuantity() {
    assertThat(line(3500, 3).getLineTotalMinor()).isEqualTo(10500);
    assertThatIllegalArgumentException().isThrownBy(() -> line(100, 51));
  }

  @Test
  void allowsPreOrdersOnlyForVisitsInsideTheAuthorizationWindow() {
    var now = Instant.parse("2026-11-13T03:00:00Z");
    assertThat(ReservationPreOrder.allowedFor(now.plus(ReservationPreOrder.WINDOW), now)).isTrue();
    assertThat(
            ReservationPreOrder.allowedFor(
                now.plus(ReservationPreOrder.WINDOW).plus(Duration.ofMinutes(1)), now))
        .isFalse();
    assertThat(ReservationPreOrder.WINDOW).isLessThan(Duration.ofDays(7));
  }
}
