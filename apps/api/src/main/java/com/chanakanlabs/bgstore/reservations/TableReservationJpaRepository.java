package com.chanakanlabs.bgstore.reservations;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TableReservationJpaRepository extends JpaRepository<TableReservationEntity, Long> {

  @Query(
      """
      select r from TableReservationEntity r
      where r.tableId in :tableIds and r.endsAt > :now
      order by r.startsAt, r.id
      """)
  List<TableReservationEntity> findUpcoming(
      @Param("tableIds") Collection<Long> tableIds, @Param("now") OffsetDateTime now);

  @Query(
      "select count(r) > 0 from TableReservationEntity r where r.tableId = :tableId and r.startsAt < :endsAt and r.endsAt > :startsAt")
  boolean existsOverlap(
      @Param("tableId") long tableId,
      @Param("startsAt") OffsetDateTime startsAt,
      @Param("endsAt") OffsetDateTime endsAt);

  @Modifying
  @Query("delete from TableReservationEntity r where r.reservationId = :reservationId")
  void deleteByReservationId(@Param("reservationId") String reservationId);
}
