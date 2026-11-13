package com.chanakanlabs.theparlor.catalog.internal.persistence;

import com.chanakanlabs.theparlor.catalog.internal.domain.GameEdition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GameEditionRepository
    extends JpaRepository<GameEdition, UUID>, JpaSpecificationExecutor<GameEdition> {
  Optional<GameEdition> findByIdAndGameId(UUID id, UUID gameId);

  List<GameEdition> findByGameIdInAndActiveTrue(Collection<UUID> gameIds);
}
