package com.chanakanlabs.bgstore.reservations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaReservationRepository extends JpaRepository<ReservationEntity, String> {

  @Query(
      """
      SELECT r FROM ReservationEntity r
      WHERE r.clientSubject = :clientSubject
        AND (:status IS NULL OR r.status = :status)
      ORDER BY r.createdAt DESC, r.id DESC
      """)
  Page<ReservationEntity> findByClientAndStatus(
      @Param("clientSubject") String clientSubject,
      @Param("status") @Nullable String status,
      Pageable pageable);

  @Query(
      """
      SELECT r FROM ReservationEntity r
      WHERE r.id = :id
        AND r.clientSubject = :clientSubject
      """)
  Optional<ReservationEntity> findByIdAndClient(
      @Param("id") String id, @Param("clientSubject") String clientSubject);

  /**
   * The client's current play session. A client has at most one checked-in reservation, so the
   * first match is the active session.
   */
  Optional<ReservationEntity> findFirstByClientSubjectAndStatus(
      String clientSubject, String status);

  /** The operational queue: reservations waiting to start and the ones in play. */
  List<ReservationEntity> findByStatusInOrderByReservationDateAscTimeSlotAsc(
      Collection<String> statuses);

  /** One page of reservations across the caller's accessible branches, chronological. */
  @Query(
      """
      SELECT r FROM ReservationEntity r
      WHERE r.branchId IN :branchIds
        AND (:status IS NULL OR r.status = :status)
        AND (:date IS NULL OR r.reservationDate = :date)
      ORDER BY r.reservationDate ASC, r.timeSlot ASC
      """)
  Page<ReservationEntity> findStaffPage(
      @Param("branchIds") Collection<UUID> branchIds,
      @Param("status") @Nullable String status,
      @Param("date") @Nullable String date,
      Pageable pageable);
}
