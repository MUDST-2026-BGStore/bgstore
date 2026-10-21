package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalCopyRepository extends JpaRepository<RentalCopy, UUID> {}
