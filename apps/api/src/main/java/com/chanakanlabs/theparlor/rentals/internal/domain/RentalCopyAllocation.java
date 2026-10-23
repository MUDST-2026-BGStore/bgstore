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
}
