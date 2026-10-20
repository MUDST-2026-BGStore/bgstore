package com.chanakanlabs.theparlor.catalog.internal.persistence;

import com.chanakanlabs.theparlor.catalog.internal.domain.GameMechanic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GameMechanicRepository
    extends JpaRepository<GameMechanic, Long>, JpaSpecificationExecutor<GameMechanic> {
  java.util.List<GameMechanic> findByNameIn(java.util.Collection<String> names);
}
