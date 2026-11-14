package com.chanakanlabs.theparlor.rentals.internal.persistence;

import com.chanakanlabs.theparlor.rentals.internal.domain.RentalRefreshment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRefreshmentRepository extends JpaRepository<RentalRefreshment, UUID> {
  List<RentalRefreshment> findByRentalIdIn(Collection<UUID> rentalIds);
}
