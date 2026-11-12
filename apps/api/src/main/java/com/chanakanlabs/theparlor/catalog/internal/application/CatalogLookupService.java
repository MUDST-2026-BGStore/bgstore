package com.chanakanlabs.theparlor.catalog.internal.application;

import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.catalog.internal.mapper.GameMapper;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameEditionRepository;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameRepository;
import com.chanakanlabs.theparlor.contracts.model.Game;
import com.chanakanlabs.theparlor.contracts.model.GameEdition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogLookupService implements CatalogLookup {
  private final GameRepository games;
  private final GameEditionRepository editions;
  private final GameMapper mapper;

  public CatalogLookupService(
      GameRepository games, GameEditionRepository editions, GameMapper mapper) {
    this.games = games;
    this.editions = editions;
    this.mapper = mapper;
  }

  public Optional<Game> activeGame(UUID id) {
    return games.findByIdAndActiveTrue(id).map(mapper::toContract);
  }

  public Optional<GameEdition> edition(UUID id) {
    return editions.findById(id).map(mapper::toEdition);
  }
}
