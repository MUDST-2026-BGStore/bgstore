package com.chanakanlabs.theparlor.rentals.internal.persistence;

import com.chanakanlabs.theparlor.rentals.internal.domain.Rental;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCopyAllocation;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCopyAllocationId;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalCopyAllocationRepository
    extends JpaRepository<RentalCopyAllocation, RentalCopyAllocationId> {
  @Query(
      "select distinct a.id.copyId from RentalCopyAllocation a, Rental r"
          + " where r.id = a.id.rentalId and a.branchId = :branchId and r.status = :status")
  Set<UUID> copiesOfRentalsWithStatus(
      @Param("branchId") UUID branchId, @Param("status") RentalStatus status);

  /** Copies of rentals whose planned end is after the instant. */
  @Query(
      "select distinct a.id.copyId from RentalCopyAllocation a, Rental r"
          + " where r.id = a.id.rentalId and a.branchId = :branchId and r.status = :status"
          + " and r.plannedEndsAt > :after")
  Set<UUID> copiesOfRentalsPlannedPast(
      @Param("branchId") UUID branchId,
      @Param("status") RentalStatus status,
      @Param("after") java.time.Instant after);

  @Query(
      "select r from RentalCopyAllocation a, Rental r where r.id = a.id.rentalId"
          + " and a.branchId = :branchId and a.id.copyId = :copyId and r.status = :status")
  List<Rental> rentalsOfCopyWithStatus(
      @Param("branchId") UUID branchId,
      @Param("copyId") UUID copyId,
      @Param("status") RentalStatus status);
}
