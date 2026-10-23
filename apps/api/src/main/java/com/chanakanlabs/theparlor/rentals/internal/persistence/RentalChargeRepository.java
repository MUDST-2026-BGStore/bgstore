package com.chanakanlabs.theparlor.rentals.internal.persistence;

import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCharge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalChargeRepository extends JpaRepository<RentalCharge, UUID> {}
