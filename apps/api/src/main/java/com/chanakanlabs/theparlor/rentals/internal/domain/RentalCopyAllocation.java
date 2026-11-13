package com.chanakanlabs.theparlor.rentals.internal.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "rental_copy_allocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalCopyAllocation {
  @EmbeddedId private RentalCopyAllocationId id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  public static RentalCopyAllocation of(UUID rentalId, UUID branchId, UUID copyId) {
    var allocation = new RentalCopyAllocation();
    allocation.id =
        new RentalCopyAllocationId(
            java.util.Objects.requireNonNull(rentalId), java.util.Objects.requireNonNull(copyId));
    allocation.branchId = java.util.Objects.requireNonNull(branchId);
    return allocation;
  }
}
