package com.chanakanlabs.theparlor.rentals.internal.persistence;

import com.chanakanlabs.theparlor.rentals.internal.domain.Rental;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalRepository extends JpaRepository<Rental, UUID> {

  @Query(
      "select (count(r) > 0) from Rental r where r.reservationId in :ids and r.status = com.chanakanlabs.theparlor.rentals.internal.domain.RentalStatus.ACTIVE")
  boolean hasActiveReservation(@Param("ids") Set<UUID> ids);
}
