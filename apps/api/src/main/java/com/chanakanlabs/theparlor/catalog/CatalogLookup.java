package com.chanakanlabs.theparlor.catalog;

import com.chanakanlabs.theparlor.contracts.model.Game;
import com.chanakanlabs.theparlor.contracts.model.GameEdition;
import java.util.Optional;
import java.util.UUID;

public interface CatalogLookup {
  Optional<Game> activeGame(UUID id);

  Optional<GameEdition> edition(UUID id);
}
