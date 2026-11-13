package com.chanakanlabs.theparlor.catalog.internal.persistence;

import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.catalog.internal.application.GamesService;
import com.chanakanlabs.theparlor.catalog.internal.domain.*;
import com.chanakanlabs.theparlor.catalog.internal.mapper.GameMapper;
import com.chanakanlabs.theparlor.catalog.internal.mapper.GameMapperImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.chanakanlabs.theparlor.catalog.internal.persistence.CatalogPersistenceTests$SqlCapture"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  GameMapperImpl.class,
  GamesService.class,
  com.chanakanlabs.theparlor.catalog.internal.application.TaxonomyService.class,
  com.chanakanlabs.theparlor.catalog.internal.application.EditionsService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogPersistenceTests {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  public static class SqlCapture implements StatementInspector {
    static final List<String> statements = Collections.synchronizedList(new ArrayList<>());

    @Override
    public String inspect(String sql) {
      statements.add(sql);
      return sql;
    }
  }

  @Autowired GameRepository games;
  @Autowired GameCategoryRepository categories;
  @Autowired GameMechanicRepository mechanics;
  @Autowired EntityManager entityManager;
  @Autowired EntityManagerFactory factory;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired GameMapper mapper;
  @Autowired GamesService service;

  <T> T tx(Supplier<T> work) {
    return new TransactionTemplate(transactions).execute(status -> work.get());
  }

  static Game.Details details(String name) {
    return new Game.Details(name, null, GameDifficulty.EASY, 1, 4, null, 20, 40, null, null);
  }

  UUID create(String name) {
    return tx(
        () -> {
          var category =
              categories.findAll().stream()
                  .findFirst()
                  .orElseGet(() -> categories.save(GameCategory.create("Family")));
          var mechanic =
              mechanics.findAll().stream()
                  .findFirst()
                  .orElseGet(() -> mechanics.save(GameMechanic.create("Drafting")));
          var game =
              Game.create(
                  details(name),
                  List.of(category),
                  List.of(mechanic),
                  Map.of("en-US", "Original"),
                  true);
          assertThat(game.getId()).isNull();
          assertThat(game.getVersion()).isNull();
          game.addImage("https://assets/one", "One");
          game.addImage("https://assets/two", null);
          games.saveAndFlush(game);
          assertThat(game.getId().version()).isEqualTo(7);
          assertThat(game.getVersion()).isZero();
          return game.getId();
        });
  }

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM orders");
    jdbc.update("DELETE FROM rentals");
    jdbc.update("DELETE FROM reservations");
    jdbc.update("DELETE FROM games");
    jdbc.update("DELETE FROM branches");
    jdbc.update("DELETE FROM game_categories");
    jdbc.update("DELETE FROM game_mechanics");
    SqlCapture.statements.clear();
    factory.unwrap(SessionFactory.class).getStatistics().clear();
  }

  @Test
  void persistsChildrenUpdatesDescriptionsAndDeletesOrphansWithoutNullingTheForeignKey() {
    var id = create("Game");
    tx(
        () -> {
          var game = games.findById(id).orElseThrow();
          assertThat(game.getImages()).extracting(GameImage::getSortOrder).containsExactly(0, 1);
          assertThat(game.getImages().getFirst().getGame()).isSameAs(game);
          game.putDescription("zh-Hant-TW", "Traditional Chinese");
          game.putDescription("en-US", "Revised");
          game.updateDetails(details("Renamed"));
          var removed = game.getImages().getFirst();
          game.removeImage(removed);
          assertThat(removed.getGame()).isNull();
          game.updateImage(game.getImages().getFirst(), "https://assets/revised", "Revised");
          game.addImage("https://assets/three", null);
          entityManager.flush();
          entityManager.clear();
          var loaded = games.findById(id).orElseThrow();
          assertThat(loaded.getName()).isEqualTo("Renamed");
          assertThat(loaded.getImages()).extracting(GameImage::getSortOrder).containsExactly(1, 2);
          assertThat(loaded.getImages().getFirst().getUrl()).isEqualTo("https://assets/revised");
          assertThat(loaded.getDescriptions())
              .containsEntry("en-US", "Revised")
              .containsEntry("zh-Hant-TW", "Traditional Chinese");
          return null;
        });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM game_images WHERE game_id = ?", Integer.class, id))
        .isEqualTo(2);
    assertThat(SqlCapture.statements).anyMatch(sql -> sql.startsWith("delete from game_images"));
    assertThat(SqlCapture.statements)
        .noneMatch(sql -> sql.startsWith("update game_images") && sql.contains("game_id"));
  }

  @Test
  void mapReplacementUpdatesOnlyTheLocaleRowAndRemovalDeletesOnlyThatRow() {
    var id = create("Game");
    tx(
        () -> {
          var game = games.findById(id).orElseThrow();
          game.getDescriptions().size();
          SqlCapture.statements.clear();
          game.putDescription("en-US", "Changed");
          entityManager.flush();
          assertThat(SqlCapture.statements)
              .anyMatch(
                  sql -> sql.startsWith("update game_descriptions") && sql.contains("locale=?"));
          assertThat(SqlCapture.statements)
              .noneMatch(sql -> sql.startsWith("delete from game_descriptions"));
          game.putDescription("th-TH", "Thai");
          entityManager.flush();
          SqlCapture.statements.clear();
          game.removeDescription("th-TH");
          entityManager.flush();
          assertThat(SqlCapture.statements)
              .anyMatch(
                  sql ->
                      sql.startsWith("delete from game_descriptions") && sql.contains("locale=?"));
          entityManager.clear();
          assertThat(games.findById(id).orElseThrow().getDescriptions())
              .containsOnlyKeys("en-US")
              .containsEntry("en-US", "Changed");
          return null;
        });
  }

  @Test
  void deletingGamePreservesSharedTaxonomyAndDeletesOwnedRows() {
    var id = create("Game");
    tx(
        () -> {
          games.deleteById(id);
          games.flush();
          return null;
        });
    assertThat(categories.count()).isEqualTo(1);
    assertThat(mechanics.count()).isEqualTo(1);
    for (var table :
        List.of("game_images", "game_descriptions", "game_category_links", "game_mechanic_links")) {
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isZero();
    }
  }

  @Test
  void setsDeduplicateDetachedAndProxyTaxonomyReferences() {
    var id = create("Game");
    var detachedCategory = categories.findAll().getFirst();
    var detachedMechanic = mechanics.findAll().getFirst();
    tx(
        () -> {
          var game = games.findById(id).orElseThrow();
          game.addCategory(detachedCategory);
          game.addMechanic(detachedMechanic);
          assertThat(game.getCategories()).hasSize(1);
          assertThat(game.getMechanics()).hasSize(1);
          var proxy = entityManager.getReference(GameCategory.class, detachedCategory.getId());
          assertThat(detachedCategory.equals(proxy)).isTrue();
          assertThat(proxy.equals(detachedCategory)).isTrue();
          assertThat(proxy.hashCode()).isEqualTo(detachedCategory.hashCode());
          games.flush();
          entityManager.clear();
          assertThat(games.findById(id).orElseThrow().getCategories()).hasSize(1);
          return null;
        });
  }

  @Test
  void mapsDetailWithinTransactionAndBatchesPageCollections() {
    var id = create("Game 0");
    for (int index = 1; index < 10; index++) create("Game " + index);
    var stats = factory.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var dto = service.getGame(id).orElseThrow();
    assertThat(dto.getId()).isEqualTo(id);
    assertThat(dto.getImages()).hasSize(2);
    assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    stats.clear();
    tx(
        () -> {
          var page = games.findAll(PageRequest.of(0, 10)).map(mapper::toContract);
          assertThat(page.getContent()).hasSize(10);
          assertThat(page.getContent()).allSatisfy(game -> assertThat(game.getImages()).hasSize(2));
          return null;
        });
    // One root SELECT, one count, four batched collection SELECTs; no Cartesian join.
    assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    tx(
        () -> {
          games.findById(id).orElseThrow().deactivate();
          return null;
        });
    assertThat(service.getGame(id)).isEmpty();
  }

  @Test
  void allControlledAssociationChangesIncrementRootVersion() {
    var id = create("Game");
    tx(
        () -> {
          var game = games.findById(id).orElseThrow();
          long before = game.getVersion();
          game.putDescription("th-TH", "Thai");
          entityManager.flush();
          assertThat(game.getVersion()).isGreaterThan(before);
          before = game.getVersion();
          game.addMechanic(mechanics.save(GameMechanic.create("Trading")));
          entityManager.flush();
          assertThat(game.getVersion()).isGreaterThan(before);
          before = game.getVersion();
          game.addCategory(categories.save(GameCategory.create("Strategy")));
          entityManager.flush();
          assertThat(game.getVersion()).isGreaterThan(before);
          before = game.getVersion();
          game.addImage("https://assets/three", null);
          entityManager.flush();
          assertThat(game.getVersion()).isGreaterThan(before);
          before = game.getVersion();
          game.updateImage(game.getImages().getFirst(), "https://assets/changed", null);
          entityManager.flush();
          assertThat(game.getVersion()).isGreaterThan(before);
          before = game.getVersion();
          game.removeImage(game.getImages().getFirst());
          entityManager.flush();
          assertThat(game.getVersion()).isGreaterThan(before);
          return null;
        });
  }

  @Test
  void staleRootAndImageUpdatesConflictAcrossPersistenceContexts() {
    for (boolean images : List.of(false, true)) {
      var id = create("Concurrent");
      try (var first = factory.createEntityManager();
          var second = factory.createEntityManager()) {
        first.getTransaction().begin();
        second.getTransaction().begin();
        var a = first.find(Game.class, id);
        var b = second.find(Game.class, id);
        // Initialize both collections before one transaction commits.
        a.getImages().size();
        b.getImages().size();
        if (images) {
          a.updateImage(a.getImages().getFirst(), "https://assets/winner", null);
          b.updateImage(b.getImages().getFirst(), "https://assets/loser", null);
        } else {
          a.updateDetails(details("Winner"));
          b.updateDetails(details("Loser"));
        }
        first.getTransaction().commit();
        assertThatThrownBy(() -> second.getTransaction().commit())
            .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
        if (second.getTransaction().isActive()) second.getTransaction().rollback();
      }
      var dto = service.getGame(id).orElseThrow();
      if (images)
        assertThat(dto.getImages().getFirst().getUrl()).isEqualTo("https://assets/winner");
      else assertThat(dto.getName()).isEqualTo("Winner");
    }
  }

  @Autowired com.chanakanlabs.theparlor.catalog.internal.application.TaxonomyService taxonomy;
  @Autowired com.chanakanlabs.theparlor.catalog.internal.application.EditionsService editionService;

  @Test
  void taxonomyStoresTranslationsAndEnforcesCanonicalKeys() {
    var category =
        taxonomy.createCategory("Strategy", Map.of("en-US", "Strategy", "zh-Hant-TW", "Chinese"));
    assertThat(taxonomy.category(category.getId()).getNames())
        .containsEntry("zh-Hant-TW", "Chinese");
    var updated = taxonomy.updateCategory(category.getId(), Map.of("en-US", "Strategic games"));
    assertThat(updated.getName()).isEqualTo("Strategy");
    assertThat(updated.getNames()).containsOnlyKeys("en-US");
    assertThatThrownBy(() -> taxonomy.updateCategory(category.getId(), Map.of("th-TH", "Thai")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(taxonomy.category(category.getId()).getNames())
        .containsEntry("en-US", "Strategic games");
    assertThatThrownBy(() -> taxonomy.createCategory("Strategy", null))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var second = taxonomy.createCategory("Family", null);
    var page = taxonomy.categories(1, null);
    assertThat(page.getItems())
        .extracting(com.chanakanlabs.theparlor.contracts.model.Category::getId)
        .containsExactly(category.getId());
    assertThat(page.getNextCursor()).isNotBlank();
    var next = taxonomy.categories(1, page.getNextCursor());
    assertThat(next.getItems())
        .extracting(com.chanakanlabs.theparlor.contracts.model.Category::getId)
        .containsExactly(second.getId());
    assertThat(next.getNextCursor()).isNull();
    assertThatThrownBy(() -> taxonomy.mechanics(1, page.getNextCursor()))
        .isInstanceOf(IllegalArgumentException.class);
    var mechanic = taxonomy.createMechanic("Drafting", null);
    assertThat(
            taxonomy
                .updateMechanic(mechanic.getId(), Map.of("en-US", "Drafting", "th-TH", "Thai"))
                .getNames())
        .hasSize(2);
  }

  @Test
  void gameSearchUsesAllFiltersAndStableFilterBoundCursors() {
    var first = create("100% Fun");
    var second = create("Other");
    var third = create("Third");
    tx(
        () -> {
          var extra = categories.save(GameCategory.create("Strategy"));
          var firstGame = games.findById(first).orElseThrow();
          firstGame.addCategory(extra);
          firstGame.updateDetails(
              new Game.Details(
                  "100% Fun", null, GameDifficulty.EASY, 2, 4, null, 20, 40, null, null));
          games.findById(third).orElseThrow().deactivate();
          return null;
        });
    var page = service.listGames(1, null, null, null, null, null, null);
    assertThat(page.getItems()).hasSize(1);
    assertThat(page.getNextCursor()).isNotNull();
    var next = service.listGames(1, page.getNextCursor(), null, null, null, null, null);
    assertThat(next.getItems()).hasSize(1);
    assertThat(next.getItems().getFirst().getId()).isNotEqualTo(page.getItems().getFirst().getId());
    assertThat(next.getNextCursor()).isNull();
    assertThatThrownBy(
            () -> service.listGames(1, page.getNextCursor(), "Changed", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.listGames(20, null, "%", null, null, null, null).getItems())
        .extracting(com.chanakanlabs.theparlor.contracts.model.Game::getId)
        .containsExactly(first);
    assertThat(
            service
                .listGames(
                    20,
                    null,
                    "FUN",
                    List.of("Strategy", "Family"),
                    List.of("Drafting"),
                    com.chanakanlabs.theparlor.contracts.model.Difficulty.EASY,
                    2)
                .getItems())
        .extracting(com.chanakanlabs.theparlor.contracts.model.Game::getId)
        .containsExactly(first);
    // Each predicate must independently exclude otherwise matching games.
    assertThat(service.listGames(20, null, null, List.of("Strategy"), null, null, null).getItems())
        .extracting(com.chanakanlabs.theparlor.contracts.model.Game::getId)
        .containsExactly(first);
    assertThat(
            service
                .listGames(20, null, null, List.of("Family", "Strategy"), null, null, null)
                .getItems())
        .extracting(com.chanakanlabs.theparlor.contracts.model.Game::getId)
        .containsExactly(first);
    assertThat(service.listGames(20, null, null, List.of("Unknown"), null, null, null).getItems())
        .isEmpty();
    assertThat(service.listGames(20, null, null, null, List.of("Unknown"), null, null).getItems())
        .isEmpty();
    assertThat(
            service
                .listGames(20, null, null, null, List.of("Drafting", "Unknown"), null, null)
                .getItems())
        .isEmpty();
    assertThat(
            service
                .listGames(
                    20,
                    null,
                    null,
                    null,
                    null,
                    com.chanakanlabs.theparlor.contracts.model.Difficulty.ADVANCED,
                    null)
                .getItems())
        .isEmpty();
    assertThat(service.listGames(20, null, null, null, null, null, 1).getItems())
        .extracting(com.chanakanlabs.theparlor.contracts.model.Game::getId)
        .containsExactly(second);
    assertThat(service.listGames(20, null, "not present", null, null, null, null).getItems())
        .isEmpty();
    assertThat(service.listGames(20, null, null, null, null, null, 5).getItems()).isEmpty();
  }

  @Test
  void partialUpdatesRetainOmittedCollectionsAndReplaceExplicitLists() {
    var id = create("Original");
    var updated =
        service.updateGame(
            id, new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest().name("Renamed"));
    assertThat(updated.getName()).isEqualTo("Renamed");
    assertThat(updated.getCategories()).containsExactly("Family");
    assertThat(updated.getDescriptions()).containsEntry("en-US", "Original");
    assertThat(updated.getImages()).hasSize(2);
    var input = new com.chanakanlabs.theparlor.contracts.model.GameImageInput("https://assets/new");
    updated =
        service.updateGame(
            id,
            new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest()
                .images(List.of(input))
                .mechanics(List.of()));
    assertThat(updated.getImages())
        .singleElement()
        .satisfies(image -> assertThat(image.getUrl()).isEqualTo("https://assets/new"));
    assertThat(updated.getMechanics()).isEmpty();
    service.updateGame(
        id, new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest().images(List.of()));
    assertThat(service.getGame(id).orElseThrow().getImages()).isEmpty();
    service.updateGame(
        id,
        new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest()
            .images(List.of(input, input)));
    assertThat(service.getGame(id).orElseThrow().getImages()).hasSize(2);
    assertThatThrownBy(
            () ->
                service.updateGame(
                    id, new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.updateGame(
                    id,
                    new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest()
                        .descriptions(Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void editionNaturalKeyAndPublicVisibilityAreEnforced() {
    var gameId = create("Game");
    var request =
        new com.chanakanlabs.theparlor.contracts.model.CreateGameEditionRequest(
            com.chanakanlabs.theparlor.contracts.model.CreateGameEditionRequest.EditionKindEnum
                .BASE_GAME,
            "en",
            true);
    var edition = editionService.create(gameId, request);
    assertThat(editionService.get(gameId, edition.getId()).getLanguageCode()).isEqualTo("en");
    assertThatThrownBy(() -> editionService.create(gameId, request))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    editionService.update(
        gameId,
        edition.getId(),
        new com.chanakanlabs.theparlor.contracts.model.UpdateGameEditionRequest().active(false));
    assertThatThrownBy(() -> editionService.get(gameId, edition.getId()))
        .isInstanceOfSatisfying(
            org.springframework.web.server.ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
    assertThat(editionService.list(gameId, 20, null, null).getItems()).isEmpty();
  }

  @Test
  void takingGameOfflinePreservesConfirmedBookingsActiveSessionsAndPlacedOrders() {
    var gameId = create("Committed");
    var branch =
        jdbc.queryForObject(
            "INSERT INTO branches(name,address) VALUES ('Branch','Address') RETURNING id",
            UUID.class);
    var table =
        jdbc.queryForObject(
            "INSERT INTO branch_tables(branch_id,label,seats) VALUES (?, 'Table',4) RETURNING id",
            UUID.class,
            branch);
    var reservation =
        jdbc.queryForObject(
            "INSERT INTO reservations(branch_id,table_id,guest_name,source,status,starts_at,ends_at,party_size,table_hourly_rate_minor,created_by_subject) VALUES (?,?,'Guest','STAFF_BOOKING','CONFIRMED',now(),now()+interval '1 hour',1,0,'staff') RETURNING id",
            UUID.class,
            branch,
            table);
    jdbc.update("INSERT INTO reservation_games VALUES (?,?,1,0)", reservation, gameId);
    var retire = new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest().active(false);
    assertThat(service.updateGame(gameId, retire).getActive()).isFalse();
    assertThat(tx(() -> games.findById(gameId).orElseThrow().isActive())).isFalse();
    assertThat(service.getGame(gameId)).isEmpty();
    jdbc.update(
        "UPDATE reservations SET status='CHECKED_IN',checked_in_at=now() WHERE id=?", reservation);
    var rental =
        jdbc.queryForObject(
            "INSERT INTO rentals(branch_id,reservation_id,started_at,planned_ends_at,checked_in_by) VALUES (?,?,now(),now()+interval '1 hour','staff') RETURNING id",
            UUID.class,
            branch,
            reservation);
    service.updateGame(
        gameId, new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest().active(true));
    assertThat(service.updateGame(gameId, retire).getActive()).isFalse();
    var edition =
        jdbc.queryForObject(
            "INSERT INTO game_editions(game_id,kind,language_code) VALUES (?,'BASE_GAME','en') RETURNING id",
            UUID.class,
            gameId);
    var order =
        jdbc.queryForObject(
            "INSERT INTO orders(branch_id,contact_name,contact_email,contact_phone,source,status,fulfillment_method,total_minor,created_by_subject) VALUES (?,'Guest','guest@example.com','123','STAFF_COUNTER','PLACED','PICKUP',0,'staff') RETURNING id",
            UUID.class,
            branch);
    jdbc.update("INSERT INTO order_items VALUES (?,?,'Committed',1,0,0,'en')", order, edition);
    service.updateGame(
        gameId, new com.chanakanlabs.theparlor.contracts.model.UpdateGameRequest().active(true));
    assertThat(service.updateGame(gameId, retire).getActive()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM reservations WHERE id=?", String.class, reservation))
        .isEqualTo("CHECKED_IN");
    assertThat(jdbc.queryForObject("SELECT status FROM rentals WHERE id=?", String.class, rental))
        .isEqualTo("ACTIVE");
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order))
        .isEqualTo("PLACED");
  }

  @Test
  void taxonomyVersionsRejectConcurrentTranslationReplacement() {
    var category = taxonomy.createCategory("Concurrent", null);
    try (var first = factory.createEntityManager();
        var second = factory.createEntityManager()) {
      first.getTransaction().begin();
      second.getTransaction().begin();
      var a = first.find(GameCategory.class, category.getId());
      var b = second.find(GameCategory.class, category.getId());
      a.replaceNames(Map.of("en-US", "Winner"));
      b.replaceNames(Map.of("en-US", "Loser"));
      first.getTransaction().commit();
      assertThatThrownBy(() -> second.getTransaction().commit())
          .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
      if (second.getTransaction().isActive()) second.getTransaction().rollback();
    }
    assertThat(taxonomy.category(category.getId()).getNames()).containsEntry("en-US", "Winner");
  }
}
