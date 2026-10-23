package com.chanakanlabs.theparlor.rentals.internal.persistence;

import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCopyAllocation;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCopyAllocationId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalCopyAllocationRepository
    extends JpaRepository<RentalCopyAllocation, RentalCopyAllocationId> {}
