package com.chanakanlabs.theparlor.catalog.internal.persistence;

import com.chanakanlabs.theparlor.catalog.internal.domain.GameCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GameCategoryRepository
    extends JpaRepository<GameCategory, Long>, JpaSpecificationExecutor<GameCategory> {
  java.util.List<GameCategory> findByNameIn(java.util.Collection<String> names);
}
