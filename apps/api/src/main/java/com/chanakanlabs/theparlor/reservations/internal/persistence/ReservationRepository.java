package com.chanakanlabs.theparlor.reservations.internal.persistence;

import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
    extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {
  Optional<Reservation> findByIdAndBranchId(UUID id, UUID branchId);

  /** Serializes every lifecycle change of one reservation. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id = :id and r.branchId = :branchId")
  Optional<Reservation> lockByIdAndBranchId(@Param("id") UUID id, @Param("branchId") UUID branchId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id = :id")
  Optional<Reservation> lockById(@Param("id") UUID id);

  /**
   * Tables with a confirmed booking overlapping [from, until) that is not over yet. Pass the
   * reservation being rescheduled as except, or any unused ID.
   */
  @Query(
      "select distinct r.tableId from Reservation r where r.branchId = :branchId"
          + " and r.status = :confirmed and r.id <> :except"
          + " and r.startsAt < :until and r.endsAt > :from and r.endsAt > :now")
  Set<UUID> tablesBookedDuring(
      @Param("branchId") UUID branchId,
      @Param("confirmed") ReservationStatus confirmed,
      @Param("from") Instant from,
      @Param("until") Instant until,
      @Param("now") Instant now,
      @Param("except") UUID except);

  /** Tables of checked-in visits whose planned end is after the instant. */
  @Query(
      "select distinct r.tableId from Reservation r where r.branchId = :branchId"
          + " and r.status = :checkedIn and r.endsAt > :after")
  Set<UUID> tablesOccupiedPast(
      @Param("branchId") UUID branchId,
      @Param("checkedIn") ReservationStatus checkedIn,
      @Param("after") Instant after);

  @Query(
      "select (count(r) > 0) from Reservation r, ReservationGame g"
          + " where g.id.reservationId = r.id and g.id.gameId = :gameId"
          + " and r.status in (com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CONFIRMED,"
          + " com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CHECKED_IN)")
  boolean hasOpenGame(@Param("gameId") UUID gameId);

  @Query(
      "select (count(r) > 0) from Reservation r"
          + " where r.customerSubject = :subject"
          + " and r.status in (com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CONFIRMED,"
          + " com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CHECKED_IN)")
  boolean hasOpenForCustomer(@Param("subject") String subject);

  @Query(
      "select distinct r.tableId from Reservation r"
          + " where r.branchId = :branchId and r.status = :status")
  Set<UUID> tablesWithStatus(
      @Param("branchId") UUID branchId, @Param("status") ReservationStatus status);

  @Query(
      "select distinct r.tableId from Reservation r"
          + " where r.branchId = :branchId and r.status = :status"
          + " and r.startsAt <= :at and r.endsAt > :at")
  Set<UUID> tablesBookedAt(
      @Param("branchId") UUID branchId,
      @Param("status") ReservationStatus status,
      @Param("at") Instant at);

  /** Open means checked in, or confirmed and not yet over. */
  @Query(
      "select (count(r) > 0) from Reservation r"
          + " where r.tableId = :tableId and r.partySize > :seats"
          + " and (r.status = :checkedIn or (r.status = :confirmed and r.endsAt > :now))")
  boolean hasOpenPartyLargerThan(
      @Param("tableId") UUID tableId,
      @Param("seats") int seats,
      @Param("confirmed") ReservationStatus confirmed,
      @Param("checkedIn") ReservationStatus checkedIn,
      @Param("now") Instant now);

  @Query(
      "select r from Reservation r where r.branchId = :branchId"
          + " and (r.status = :checkedIn or (r.status = :confirmed and r.endsAt > :now))")
  List<Reservation> openAtBranch(
      @Param("branchId") UUID branchId,
      @Param("confirmed") ReservationStatus confirmed,
      @Param("checkedIn") ReservationStatus checkedIn,
      @Param("now") Instant now);
}
