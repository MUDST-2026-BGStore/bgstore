package com.chanakanlabs.theparlor.reservations.internal.persistence;

import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationCopyHold;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationCopyHoldId;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationCopyHoldRepository
    extends JpaRepository<ReservationCopyHold, ReservationCopyHoldId> {
  @Query(
      "select distinct h.id.copyId from ReservationCopyHold h, Reservation r"
          + " where r.id = h.id.reservationId and h.branchId = :branchId and r.status = :status"
          + " and r.startsAt <= :at and r.endsAt > :at")
  Set<UUID> copiesHeldAt(
      @Param("branchId") UUID branchId,
      @Param("status") ReservationStatus status,
      @Param("at") Instant at);

  /** Copies held by reservations with the status whose window overlaps (after, before). */
  @Query(
      "select distinct h.id.copyId from ReservationCopyHold h, Reservation r"
          + " where r.id = h.id.reservationId and h.branchId = :branchId and r.status = :status"
          + " and r.endsAt > :after and r.startsAt < :before")
  Set<UUID> copiesHeldDuring(
      @Param("branchId") UUID branchId,
      @Param("status") ReservationStatus status,
      @Param("after") Instant after,
      @Param("before") Instant before);

  List<ReservationCopyHold> findByIdReservationId(UUID reservationId);

  @Modifying
  @Query("delete from ReservationCopyHold h where h.id.reservationId = :reservationId")
  void deleteOf(@Param("reservationId") UUID reservationId);

  /** Reservations with the status holding the copy whose window ends after the instant. */
  @Query(
      "select r from ReservationCopyHold h, Reservation r where r.id = h.id.reservationId"
          + " and h.branchId = :branchId and h.id.copyId = :copyId and r.status = :status"
          + " and r.endsAt > :after order by r.startsAt")
  List<Reservation> holdersEndingAfter(
      @Param("branchId") UUID branchId,
      @Param("copyId") UUID copyId,
      @Param("status") ReservationStatus status,
      @Param("after") Instant after);
}
