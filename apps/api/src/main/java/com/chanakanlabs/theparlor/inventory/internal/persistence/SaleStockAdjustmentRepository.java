package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.SaleStockAdjustment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleStockAdjustmentRepository extends JpaRepository<SaleStockAdjustment, UUID> {}
