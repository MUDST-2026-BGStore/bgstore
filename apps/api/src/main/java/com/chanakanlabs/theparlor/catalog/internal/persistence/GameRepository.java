package com.chanakanlabs.theparlor.catalog.internal.persistence;

import com.chanakanlabs.theparlor.catalog.internal.domain.Game;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GameRepository extends JpaRepository<Game, UUID>, JpaSpecificationExecutor<Game> {
  Optional<Game> findByIdAndActiveTrue(UUID id);
}
