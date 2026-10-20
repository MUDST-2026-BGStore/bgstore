package com.chanakanlabs.theparlor.catalog.internal.application;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.REQUIRED_LOCALE;

import com.chanakanlabs.theparlor.catalog.internal.domain.Game.Details;
import com.chanakanlabs.theparlor.catalog.internal.domain.Game.ImageDetails;
import com.chanakanlabs.theparlor.catalog.internal.domain.GameCategory;
import com.chanakanlabs.theparlor.catalog.internal.domain.GameDifficulty;
import com.chanakanlabs.theparlor.catalog.internal.domain.GameMechanic;
import com.chanakanlabs.theparlor.catalog.internal.mapper.GameMapper;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameCategoryRepository;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameMechanicRepository;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameRepository;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameSpecifications;
import com.chanakanlabs.theparlor.contracts.model.CreateGameRequest;
import com.chanakanlabs.theparlor.contracts.model.Difficulty;
import com.chanakanlabs.theparlor.contracts.model.Game;
import com.chanakanlabs.theparlor.contracts.model.GamePage;
import com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GamesService {
  private final GameRepository games;
  private final GameMapper gameMapper;

  private final GameCategoryRepository categories;
  private final GameMechanicRepository mechanics;

  public GamesService(
      GameRepository games,
      GameMapper gameMapper,
      GameCategoryRepository categories,
      GameMechanicRepository mechanics) {
    this.games = games;
    this.gameMapper = gameMapper;
    this.categories = categories;
    this.mechanics = mechanics;
  }

  @Transactional(readOnly = true)
  public Optional<Game> getGame(UUID gameId) {
    return games.findByIdAndActiveTrue(gameId).map(gameMapper::toContract);
  }

  @Transactional(readOnly = true)
  public GamePage listGames(
      Integer requestedLimit,
      String cursor,
      String q,
      List<String> categories,
      List<String> mechanics,
      Difficulty difficulty,
      Integer players) {
    int limit = CatalogCursor.limit(requestedLimit);
    var filters =
        new GameSearchCriteria(
            q,
            categories,
            mechanics,
            difficulty == null ? null : GameDifficulty.valueOf(difficulty.name()),
            players);
    String cursorFilters = CatalogCursor.gameFilters(filters);
    String key = CatalogCursor.after(cursor, "games", cursorFilters);
    UUID after = key == null ? null : UUID.fromString(key);
    if (key != null && !after.toString().equals(key))
      throw new IllegalArgumentException("Invalid cursor ID");
    var rows =
        games.findBy(
            GameSpecifications.matching(filters, after),
            query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var items = rows.stream().limit(limit).map(gameMapper::toContract).toList();
    var result = new GamePage(items);
    if (rows.size() > limit)
      result.setNextCursor(CatalogCursor.next("games", cursorFilters, items.getLast().getId()));
    return result;
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Game createGame(CreateGameRequest request) {
    var details =
        new Details(
            request.getName(),
            request.getDesigner(),
            difficulty(request.getDifficulty()),
            request.getMinPlayers(),
            request.getMaxPlayers(),
            request.getMinAge(),
            request.getPlayMinutesMin(),
            request.getPlayMinutesMax(),
            request.getTeachMinutes(),
            request.getSetupMinutes());
    if (request.getDescriptions() == null)
      throw new IllegalArgumentException("Description is required for locale " + REQUIRED_LOCALE);
    var entity =
        com.chanakanlabs.theparlor.catalog.internal.domain.Game.create(
            details,
            resolveCategories(request.getCategories()),
            resolveMechanics(request.getMechanics()),
            request.getDescriptions(),
            request.getActive());
    if (request.getImages() != null) entity.replaceImages(images(request.getImages()));
    return gameMapper.toContract(games.saveAndFlush(entity));
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Game updateGame(UUID id, UpdateGameRequest request) {
    if (request.getName() == null
        && request.getDesigner() == null
        && request.getDifficulty() == null
        && request.getMinPlayers() == null
        && request.getMaxPlayers() == null
        && request.getMinAge() == null
        && request.getPlayMinutesMin() == null
        && request.getPlayMinutesMax() == null
        && request.getTeachMinutes() == null
        && request.getSetupMinutes() == null
        && request.getActive() == null
        && request.getCategories() == null
        && request.getMechanics() == null
        && request.getDescriptions() == null
        && request.getImages() == null)
      throw new IllegalArgumentException("At least one update property is required");
    var entity =
        games
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
    var details =
        new Details(
            or(request.getName(), entity.getName()),
            or(request.getDesigner(), entity.getDesigner()),
            or(difficulty(request.getDifficulty()), entity.getDifficulty()),
            or(request.getMinPlayers(), entity.getMinPlayers()),
            or(request.getMaxPlayers(), entity.getMaxPlayers()),
            or(request.getMinAge(), entity.getMinAge()),
            or(request.getPlayMinutesMin(), entity.getPlayMinutesMin()),
            or(request.getPlayMinutesMax(), entity.getPlayMinutesMax()),
            or(request.getTeachMinutes(), entity.getTeachMinutes()),
            or(request.getSetupMinutes(), entity.getSetupMinutes()));
    entity.updateDetails(details);
    if (request.getCategories() != null)
      entity.replaceCategories(resolveCategories(request.getCategories()));
    if (request.getMechanics() != null)
      entity.replaceMechanics(resolveMechanics(request.getMechanics()));
    if (request.getDescriptions() != null) entity.replaceDescriptions(request.getDescriptions());
    if (request.getImages() != null) entity.replaceImages(images(request.getImages()));
    if (request.getActive() != null) {
      if (request.getActive()) entity.activate();
      else {
        // TODO(catalog-offline): Define how staff resolve affected reservations, active play
        // sessions and placed orders (notification, substitution, rescheduling or refund).
        // For now, taking a game offline leaves those commitments unchanged.
        entity.deactivate();
      }
    }
    games.flush();
    return gameMapper.toContract(entity);
  }

  private List<GameCategory> resolveCategories(List<String> names) {
    if (names == null || names.isEmpty())
      throw new IllegalArgumentException("At least one category is required");
    if (names.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Taxonomy keys cannot be null");
    var unique = new java.util.HashSet<>(names);
    var entities = categories.findByNameIn(unique);
    if (entities.size() != unique.size())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    return entities;
  }

  private List<GameMechanic> resolveMechanics(List<String> names) {
    if (names == null || names.isEmpty()) return List.of();
    if (names.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Taxonomy keys cannot be null");
    var unique = new java.util.HashSet<>(names);
    var entities = mechanics.findByNameIn(unique);
    if (entities.size() != unique.size())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mechanic not found");
    return entities;
  }

  private static List<ImageDetails> images(
      List<com.chanakanlabs.theparlor.contracts.model.GameImageInput> values) {
    if (values.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Images cannot contain null entries");
    return values.stream()
        .map(value -> new ImageDetails(value.getUrl(), value.getAltText()))
        .toList();
  }

  private static GameDifficulty difficulty(Difficulty value) {
    return value == null ? null : GameDifficulty.valueOf(value.name());
  }

  private static <T> T or(T update, T previous) {
    return update == null ? previous : update;
  }
}
