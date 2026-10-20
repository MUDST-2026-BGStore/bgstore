package com.chanakanlabs.theparlor.catalog.internal.application;

import com.chanakanlabs.theparlor.catalog.internal.domain.EditionKind;
import com.chanakanlabs.theparlor.catalog.internal.mapper.GameMapper;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameEditionRepository;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameRepository;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EditionsService {
  private final GameRepository games;
  private final GameEditionRepository editions;
  private final GameMapper mapper;

  public EditionsService(GameRepository games, GameEditionRepository editions, GameMapper mapper) {
    this.games = games;
    this.editions = editions;
    this.mapper = mapper;
  }

  public GameEdition get(UUID gameId, UUID editionId) {
    requireGame(gameId);
    var edition =
        editions.findByIdAndGameId(editionId, gameId).orElseThrow(EditionsService::missing);
    if (!edition.isActive() && !manager()) throw missing();
    return mapper.toEdition(edition);
  }

  public GameEditionPage list(UUID gameId, Integer requestedLimit, String cursor, Boolean active) {
    requireGame(gameId);
    boolean onlyActive = active == null || active;
    if (!onlyActive && !manager()) throw missing();
    int limit = CatalogCursor.limit(requestedLimit);
    String filters = gameId + ":" + onlyActive;
    String key = CatalogCursor.after(cursor, "editions", filters);
    UUID after = key == null ? null : UUID.fromString(key);
    if (key != null && !after.toString().equals(key))
      throw new IllegalArgumentException("Invalid cursor ID");
    Specification<com.chanakanlabs.theparlor.catalog.internal.domain.GameEdition> spec =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("gameId"), gameId),
                cb.equal(root.get("active"), onlyActive),
                after == null ? cb.conjunction() : cb.greaterThan(root.get("id"), after));
    var rows = editions.findBy(spec, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var items = rows.stream().limit(limit).map(mapper::toEdition).toList();
    var result = new GameEditionPage(items);
    if (rows.size() > limit)
      result.setNextCursor(CatalogCursor.next("editions", filters, items.getLast().getId()));
    return result;
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public GameEdition create(UUID gameId, CreateGameEditionRequest request) {
    requireGame(gameId);
    var entity =
        com.chanakanlabs.theparlor.catalog.internal.domain.GameEdition.create(
            gameId,
            EditionKind.valueOf(request.getEditionKind().name()),
            request.getLanguageCode(),
            Boolean.TRUE.equals(request.getActive()));
    return mapper.toEdition(editions.saveAndFlush(entity));
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public GameEdition update(UUID gameId, UUID id, UpdateGameEditionRequest request) {
    requireGame(gameId);
    if (request.getEditionKind() == null
        && request.getLanguageCode() == null
        && request.getActive() == null) {
      throw new IllegalArgumentException("At least one update property is required");
    }
    var entity = editions.findByIdAndGameId(id, gameId).orElseThrow(EditionsService::missing);
    entity.update(
        request.getEditionKind() == null
            ? entity.getKind()
            : EditionKind.valueOf(request.getEditionKind().name()),
        request.getLanguageCode() == null ? entity.getLanguageCode() : request.getLanguageCode(),
        request.getActive() == null ? entity.isActive() : request.getActive());
    editions.flush();
    return mapper.toEdition(entity);
  }

  private void requireGame(UUID gameId) {
    var game = games.findById(gameId).orElseThrow(EditionsService::missing);
    if (!game.isActive() && !manager()) throw missing();
  }

  private static boolean manager() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.isAuthenticated()
        && auth.getAuthorities().stream()
            .anyMatch(
                role ->
                    Objects.equals(role.getAuthority(), "ROLE_MANAGER")
                        || Objects.equals(role.getAuthority(), "ROLE_ADMIN"));
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Edition or game not found");
  }
}
