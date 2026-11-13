package com.chanakanlabs.theparlor.rentals.internal.persistence;

import com.chanakanlabs.theparlor.rentals.internal.domain.Rental;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalRepository
    extends JpaRepository<Rental, UUID>, JpaSpecificationExecutor<Rental> {

  Optional<Rental> findByIdAndBranchId(UUID id, UUID branchId);

  Optional<Rental> findByReservationId(UUID reservationId);

  List<Rental> findByReservationIdIn(Collection<UUID> reservationIds);

  /** Serializes checkout of one rental. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Rental r where r.id = :id and r.branchId = :branchId")
  Optional<Rental> lockByIdAndBranchId(@Param("id") UUID id, @Param("branchId") UUID branchId);
}
