package com.chanakanlabs.theparlor.pantry.internal.persistence;

import com.chanakanlabs.theparlor.pantry.internal.domain.MenuStockAdjustment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuStockAdjustmentRepository extends JpaRepository<MenuStockAdjustment, UUID> {}
