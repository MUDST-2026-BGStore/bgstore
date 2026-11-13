package com.chanakanlabs.theparlor.reservations.internal.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A physical copy held for a reservation's window while the reservation is confirmed. */
@Entity
@Table(name = "reservation_copy_holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationCopyHold {
  @EmbeddedId private ReservationCopyHoldId id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  public static ReservationCopyHold of(UUID reservationId, UUID branchId, UUID copyId) {
    var hold = new ReservationCopyHold();
    hold.id =
        new ReservationCopyHoldId(
            java.util.Objects.requireNonNull(reservationId),
            java.util.Objects.requireNonNull(copyId));
    hold.branchId = java.util.Objects.requireNonNull(branchId);
    return hold;
  }
}
