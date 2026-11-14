package com.chanakanlabs.theparlor.reservations.internal.domain;

import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation.Party;
import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation.Plan;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTests {
  static final Instant START = Instant.parse("2026-11-12T05:00:00Z");
  static final Instant END = START.plusSeconds(7200);

  static Reservation booked() {
    return Reservation.book(
        UUID.randomUUID(),
        new Party(null, null, " Guest ", null),
        ReservationSource.WALK_IN,
        new Plan(UUID.randomUUID(), START, END, 4, 5000),
        null,
        "staff-1");
  }

  @Test
  void keepsOnlyTheMicrosecondsTheDatabaseStores() {
    // Linux clocks give nanoseconds; PostgreSQL keeps microseconds. A booking response
    // must show the same time a later read returns (CI found the mismatch).
    var plan =
        new Plan(
            UUID.randomUUID(),
            Instant.parse("2026-11-14T10:00:00.123456789Z"),
            Instant.parse("2026-11-14T12:00:00.000000999Z"),
            4,
            5000);
    assertThat(plan.startsAt()).isEqualTo(Instant.parse("2026-11-14T10:00:00.123456Z"));
    assertThat(plan.endsAt()).isEqualTo(Instant.parse("2026-11-14T12:00:00Z"));
  }

  @Test
  void aBookingIsForARegisteredCustomerOrAGuestAndHasAValidPlan() {
    assertThat(booked().getGuestName()).isEqualTo("Guest");
    assertThatThrownBy(() -> new Party(UUID.randomUUID(), "sub", "Guest", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Party(null, null, " ", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Party(UUID.randomUUID(), null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Plan(UUID.randomUUID(), START, START, 4, 5000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Plan(UUID.randomUUID(), START, END, 21, 5000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checkInIsOnlyPossibleInsideTheWindowAndLeadsToCompletion() {
    var early = booked();
    assertThatThrownBy(() -> early.checkIn("staff-1", START.minusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> early.checkIn("staff-1", END))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> early.complete(END)).isInstanceOf(IllegalStateException.class);

    var visit = booked();
    visit.checkIn("staff-1", START);
    assertThat(visit.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
    assertThatThrownBy(() -> visit.cancel("staff-1", "Late", START))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> visit.reschedule(new Plan(UUID.randomUUID(), START, END, 2, 0), null))
        .isInstanceOf(IllegalStateException.class);
    visit.complete(END.plusSeconds(600));
    visit.complete(END.plusSeconds(900));
    assertThat(visit.getCompletedAt()).isEqualTo(END.plusSeconds(600));
    assertThat(visit.getCheckedInAt()).isEqualTo(START);
  }

  @Test
  void cancellationAndNoShowAreTerminalAndRepeatable() {
    var cancelled = booked();
    assertThat(cancelled.cancel("staff-1", "Changed plans", START)).isTrue();
    assertThat(cancelled.cancel("staff-2", "Again", START)).isFalse();
    assertThat(cancelled.getCancelledBy()).isEqualTo("staff-1");
    assertThatThrownBy(() -> cancelled.markNoShow("staff-1", "Missing", START))
        .isInstanceOf(IllegalStateException.class);

    var missed = booked();
    assertThatThrownBy(() -> missed.markNoShow("staff-1", "Missing", START.minusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> missed.markNoShow("staff-1", " ", START))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(missed.markNoShow("staff-1", "Never arrived", START)).isTrue();
    assertThat(missed.markNoShow("staff-2", "Again", START)).isFalse();
    assertThat(missed.getNoShowReason()).isEqualTo("Never arrived");
    assertThatThrownBy(() -> missed.checkIn("staff-1", START))
        .isInstanceOf(IllegalStateException.class);
  }
}
