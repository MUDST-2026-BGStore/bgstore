package com.chanakanlabs.theparlor.catalog;

import com.chanakanlabs.theparlor.contracts.model.Game;
import com.chanakanlabs.theparlor.contracts.model.GameEdition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogLookup {
  Optional<Game> activeGame(UUID id);

  Optional<GameEdition> edition(UUID id);

  /**
   * The active games among these IDs, in no particular order. A non-null nameQuery keeps only games
   * whose name contains it, ignoring case.
   */
  List<Game> activeGames(Collection<UUID> ids, String nameQuery);

  /** These games whatever their state, for showing lines that can no longer be bought. */
  List<Game> games(Collection<UUID> ids);

  /** These editions whatever their state. */
  List<GameEdition> editions(Collection<UUID> ids);

  /** Active editions of these games, in no particular order. */
  List<GameEdition> activeEditions(Collection<UUID> gameIds);
}
