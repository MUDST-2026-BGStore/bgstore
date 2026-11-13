package com.chanakanlabs.theparlor.pantry.internal.persistence;

import com.chanakanlabs.theparlor.pantry.internal.domain.MenuItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
  List<MenuItem> findByActiveTrueOrderByCategoryAscSkuAsc();

  List<MenuItem> findByIdIn(Collection<UUID> ids);
}
