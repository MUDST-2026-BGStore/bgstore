package com.chanakanlabs.theparlor.catalog.internal.application;

import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.catalog.internal.mapper.GameMapper;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameEditionRepository;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameRepository;
import com.chanakanlabs.theparlor.contracts.model.Game;
import com.chanakanlabs.theparlor.contracts.model.GameEdition;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
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

  public List<Game> activeGames(Collection<UUID> ids, String nameQuery) {
    if (ids.isEmpty()) return List.of();
    Specification<com.chanakanlabs.theparlor.catalog.internal.domain.Game> matching =
        (root, query, cb) -> {
          var active = cb.and(root.get("id").in(ids), cb.isTrue(root.get("active")));
          if (nameQuery == null) return active;
          String pattern =
              "%"
                  + nameQuery
                      .toLowerCase(Locale.ROOT)
                      .replace("!", "!!")
                      .replace("%", "!%")
                      .replace("_", "!_")
                  + "%";
          return cb.and(active, cb.like(cb.lower(root.get("name")), pattern, '!'));
        };
    return games.findAll(matching).stream().map(mapper::toContract).toList();
  }

  public List<Game> games(Collection<UUID> ids) {
    return ids.isEmpty()
        ? List.of()
        : games.findAllById(ids).stream().map(mapper::toContract).toList();
  }

  public List<GameEdition> editions(Collection<UUID> ids) {
    return ids.isEmpty()
        ? List.of()
        : editions.findAllById(ids).stream().map(mapper::toEdition).toList();
  }

  public List<GameEdition> activeEditions(Collection<UUID> gameIds) {
    if (gameIds.isEmpty()) return List.of();
    return editions.findByGameIdInAndActiveTrue(gameIds).stream().map(mapper::toEdition).toList();
  }
}
