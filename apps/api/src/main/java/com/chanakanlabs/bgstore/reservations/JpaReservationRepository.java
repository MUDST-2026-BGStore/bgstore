package com.chanakanlabs.bgstore.reservations;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

interface JpaReservationRepository extends JpaRepository<ReservationEntity, String> {

  @Query(
      """
      SELECT r FROM ReservationEntity r
      WHERE (r.clientSubject = :clientSubject OR r.clientSubject = 'seed-client')
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
        AND (r.clientSubject = :clientSubject OR r.clientSubject = 'seed-client')
      """)
  Optional<ReservationEntity> findByIdAndClient(
      @Param("id") String id, @Param("clientSubject") String clientSubject);
}
