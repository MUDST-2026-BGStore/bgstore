package com.chanakanlabs.bgstore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Exercises the game endpoints against a real PostgreSQL, migrations included. */
@SpringBootTest(
    properties = {
      "management.logging.export.otlp.enabled=false",
      "management.otlp.metrics.export.enabled=false",
      "management.tracing.export.enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
class GameApiIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.1-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.4-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "test-password");

  /** Seeded by {@code V2__games.sql}; the game screens address branches by id. */
  private static final UUID CENTRAL_RAMA_II =
      UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000001");

  private static final UUID BIG_C_RAMA_I = UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000002");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "test-password");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper json;
  @Autowired private DSLContext database;

  @BeforeEach
  void clearCatalogue() {
    database.execute("delete from game");
  }

  @Test
  void anonymousUserCannotReachTheGameEndpoints() throws Exception {
    mockMvc.perform(get("/api/v1/games")).andExpect(status().isUnauthorized());
  }

  @Test
  void refusesAWriteThatCarriesNoCsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/games")
                .with(oidcLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("Uno", "card", 2, 10).toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void listsTheSeededBranchesByName() throws Exception {
    mockMvc
        .perform(get("/api/v1/branches").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(6))
        .andExpect(jsonPath("$.items[0].name").value("Big C Rama I"))
        .andExpect(jsonPath("$.items[5].name").value("Thonglor"));
  }

  @Test
  void createsAGameAndReadsItBackWithItsPerBranchStock() throws Exception {
    var payload = payload("Ticket to Ride", "family", 2, 5);
    payload.put("description", "Build routes across the map.");
    payload.put("playTimeMinutes", 60);
    payload.put("difficulty", "Easy to teach");
    payload.putArray("tags").add("beginner friendly").add("30–60 min");
    copies(payload, CENTRAL_RAMA_II, 2);

    var id =
        create(payload)
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.title").value("Ticket to Ride"))
            .andReturn();

    var created = json.readTree(id.getResponse().getContentAsString()).get("id").asText();
    assertThat(id.getResponse().getHeader("Location")).isEqualTo("/api/v1/games/" + created);

    mockMvc
        .perform(get("/api/v1/games/{id}", created).with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Build routes across the map."))
        .andExpect(jsonPath("$.playTimeMinutes").value(60))
        .andExpect(jsonPath("$.difficulty").value("Easy to teach"))
        .andExpect(jsonPath("$.tags[0]").value("beginner friendly"))
        .andExpect(jsonPath("$.lifecycle").value("active"))
        .andExpect(jsonPath("$.status").value("available"))
        .andExpect(jsonPath("$.totalCopies").value(2))
        .andExpect(jsonPath("$.branchCount").value(1))
        .andExpect(jsonPath("$.lastPlayedAt").doesNotExist())
        // Every branch appears so the copies-by-branch table can show the empty ones.
        .andExpect(jsonPath("$.stock.length()").value(6))
        .andExpect(jsonPath("$.stock[2].branchName").value("Central Rama II"))
        .andExpect(jsonPath("$.stock[2].copies").value(2))
        .andExpect(jsonPath("$.stock[2].status").value("available"))
        .andExpect(jsonPath("$.stock[0].status").value("notStocked"));
  }

  @Test
  void namesTheBranchOnAListRowOnlyWhileTheGameIsStockedAtOne() throws Exception {
    var single = payload("Uno", "card", 2, 10);
    copies(single, CENTRAL_RAMA_II, 5);
    create(single).andExpect(status().isCreated());

    var spread = payload("Catan", "strategy", 3, 4);
    copies(spread, CENTRAL_RAMA_II, 1);
    copies(spread, BIG_C_RAMA_I, 2);
    create(spread).andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/games").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("Catan"))
        .andExpect(jsonPath("$.items[0].branchCount").value(2))
        .andExpect(jsonPath("$.items[0].copies").value(3))
        .andExpect(jsonPath("$.items[0].branchName").doesNotExist())
        .andExpect(jsonPath("$.items[1].title").value("Uno"))
        .andExpect(jsonPath("$.items[1].branchName").value("Central Rama II"));
  }

  @Test
  void narrowsBothTheRowsAndTheirFiguresToTheFilteredBranch() throws Exception {
    var spread = payload("Catan", "strategy", 3, 4);
    copies(spread, CENTRAL_RAMA_II, 1);
    copies(spread, BIG_C_RAMA_I, 2);
    create(spread).andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/games").param("branchId", BIG_C_RAMA_I.toString()).with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].copies").value(2))
        .andExpect(jsonPath("$.items[0].branchName").value("Big C Rama I"))
        .andExpect(jsonPath("$.stats.availableNow").value(2));
  }

  @Test
  void filtersOnCategoryStatusAndACaseInsensitiveTitleFragment() throws Exception {
    var uno = payload("Uno", "card", 2, 10);
    copies(uno, CENTRAL_RAMA_II, 5);
    create(uno).andExpect(status().isCreated());
    create(payload("Splendor", "strategy", 2, 4)).andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/games").param("category", "card").with(oidcLogin()))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Uno"));

    mockMvc
        .perform(get("/api/v1/games").param("status", "notStocked").with(oidcLogin()))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Splendor"));

    mockMvc
        .perform(get("/api/v1/games").param("search", "nO").with(oidcLogin()))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Uno"));

    // A wildcard typed into the search box matches itself, not everything.
    mockMvc
        .perform(get("/api/v1/games").param("search", "%").with(oidcLogin()))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void reportsTheFilteredTotalsOnEveryPage() throws Exception {
    create(payload("Azul", "family", 2, 4)).andExpect(status().isCreated());
    var uno = payload("Uno", "card", 2, 10);
    copies(uno, CENTRAL_RAMA_II, 5);
    create(uno).andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/games").param("page", "1").param("size", "1").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Uno"))
        .andExpect(jsonPath("$.page.number").value(1))
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.page.totalPages").value(2))
        .andExpect(jsonPath("$.stats.titles").value(2))
        .andExpect(jsonPath("$.stats.availableNow").value(5));
  }

  @Test
  void answersAnEmptyResultWithZeroedTotalsRatherThanNothing() throws Exception {
    mockMvc
        .perform(get("/api/v1/games").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.page.totalElements").value(0))
        .andExpect(jsonPath("$.page.totalPages").value(0))
        .andExpect(jsonPath("$.stats.titles").value(0))
        .andExpect(jsonPath("$.stats.availableNow").value(0))
        .andExpect(jsonPath("$.stats.inUse").value(0));
  }

  @Test
  void replacesTheStoredGameAndItsStockOnUpdate() throws Exception {
    var id = createGame(payloadWithCopies("Uno", "card", 2, 10, CENTRAL_RAMA_II, 5));

    var update = payload("Uno Flip", "party", 2, 8);
    copies(update, BIG_C_RAMA_I, 3);

    mockMvc
        .perform(
            put("/api/v1/games/{id}", id)
                .with(oidcLogin())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Uno Flip"))
        .andExpect(jsonPath("$.category").value("party"))
        .andExpect(jsonPath("$.totalCopies").value(3))
        .andExpect(jsonPath("$.branchCount").value(1))
        // The branch left out of the payload no longer holds copies.
        .andExpect(jsonPath("$.stock[2].branchName").value("Central Rama II"))
        .andExpect(jsonPath("$.stock[2].copies").value(0));
  }

  @Test
  void retiresAGameInsteadOfDroppingItSoItStaysInTheInventory() throws Exception {
    var id = createGame(payloadWithCopies("Dixit", "party", 3, 6, CENTRAL_RAMA_II, 3));

    mockMvc
        .perform(delete("/api/v1/games/{id}", id).with(oidcLogin()).with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/games/{id}", id).with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycle").value("retired"))
        .andExpect(jsonPath("$.status").value("retired"));

    mockMvc
        .perform(get("/api/v1/games").param("status", "retired").with(oidcLogin()))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Dixit"));
  }

  @Test
  void bringsARetiredGameBackWhenAnUpdateSaysItIsActiveAgain() throws Exception {
    var id = createGame(payloadWithCopies("Dixit", "party", 3, 6, CENTRAL_RAMA_II, 3));
    mockMvc
        .perform(delete("/api/v1/games/{id}", id).with(oidcLogin()).with(csrf()))
        .andExpect(status().isNoContent());

    var revive = payloadWithCopies("Dixit", "party", 3, 6, CENTRAL_RAMA_II, 3);
    revive.put("lifecycle", "active");

    mockMvc
        .perform(
            put("/api/v1/games/{id}", id)
                .with(oidcLogin())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(revive.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("available"));
  }

  @Test
  void answersAnUnknownGameWithAProblemDetail() throws Exception {
    var unknown = UUID.randomUUID();

    mockMvc
        .perform(get("/api/v1/games/{id}", unknown).with(oidcLogin()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Not Found"));

    mockMvc
        .perform(delete("/api/v1/games/{id}", unknown).with(oidcLogin()).with(csrf()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/games/{id}", unknown)
                .with(oidcLogin())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("Uno", "card", 2, 10).toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsASchemaViolationWithTheOffendingFieldNames() throws Exception {
    var payload = payload("Uno", "card", 0, 10);

    create(payload)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.errors[0].field").value("minPlayers"))
        .andExpect(jsonPath("$.errors[0].message").value("invalid"));
  }

  @Test
  void separatesAFieldLeftBlankFromAFieldFilledInWrongly() throws Exception {
    var blankTitle = payload("", "card", 2, 10);

    create(blankTitle)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("title"))
        .andExpect(jsonPath("$.errors[0].message").value("required"));

    var noCategory = json.createObjectNode();
    noCategory.put("title", "Uno");
    noCategory.put("minPlayers", 2);
    noCategory.put("maxPlayers", 10);

    create(noCategory)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("category"))
        .andExpect(jsonPath("$.errors[0].message").value("required"));
  }

  @Test
  void namesTheBodyPropertyJacksonCouldNotRead() throws Exception {
    var unknownCategory = payload("Uno", "boardgame", 2, 10);

    create(unknownCategory)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("category"))
        .andExpect(jsonPath("$.errors[0].message").value("invalid"));

    var badCopies = payload("Uno", "card", 2, 10);
    badCopies.putArray("copies").addObject().put("branchId", "not-a-uuid").put("copies", 1);

    create(badCopies)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("copies[0].branchId"));
  }

  @Test
  void answersUnreadableJsonAsAMalformedRequestRatherThanARuleViolation() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/games")
                .with(oidcLogin())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void rejectsAPageSizePastTheContractMaximum() throws Exception {
    mockMvc
        .perform(get("/api/v1/games").param("size", "500").with(oidcLogin()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }

  @Test
  void keepsReportingTheFilteredTotalsOnAPagePastTheLastOne() throws Exception {
    var uno = payloadWithCopies("Uno", "card", 2, 10, CENTRAL_RAMA_II, 5);
    create(uno).andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/games").param("page", "9").param("size", "20").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        // The stat tiles describe the filtered set, not the empty page.
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.page.totalPages").value(1))
        .andExpect(jsonPath("$.stats.titles").value(1))
        .andExpect(jsonPath("$.stats.availableNow").value(5));
  }

  @Test
  void rejectsAPlayerRangeAndAnUnknownBranchInOnePass() throws Exception {
    var payload = payload("Uno", "card", 8, 2);
    copies(payload, UUID.randomUUID(), 1);

    create(payload)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("maxPlayers"))
        .andExpect(jsonPath("$.errors[0].message").value("belowMinimum"))
        .andExpect(jsonPath("$.errors[1].field").value("copies[0].branchId"))
        .andExpect(jsonPath("$.errors[1].message").value("unknownBranch"));
  }

  @Test
  void rejectsAListFilteredByABranchThatDoesNotExist() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/games").param("branchId", UUID.randomUUID().toString()).with(oidcLogin()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("branchId"))
        .andExpect(jsonPath("$.errors[0].message").value("unknownBranch"));
  }

  @Test
  void keepsCopiesThatAPlaySessionIsHoldingAndReportsThemAsInUse() throws Exception {
    var id = createGame(payloadWithCopies("Splendor", "strategy", 2, 4, CENTRAL_RAMA_II, 2));

    // Stands in for the play-session module, which does not exist yet.
    database.execute(
        "update game_branch_stock set copies_in_use = 2 where game_id = ?", UUID.fromString(id));

    mockMvc
        .perform(get("/api/v1/games").with(oidcLogin()))
        .andExpect(jsonPath("$.items[0].status").value("allCopiesOut"))
        .andExpect(jsonPath("$.items[0].available").value(0))
        .andExpect(jsonPath("$.stats.inUse").value(2));

    var shrink = payloadWithCopies("Splendor", "strategy", 2, 4, CENTRAL_RAMA_II, 1);
    mockMvc
        .perform(
            put("/api/v1/games/{id}", id)
                .with(oidcLogin())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(shrink.toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("copies[0].copies"))
        .andExpect(jsonPath("$.errors[0].message").value("belowInUse"));

    var drop = payload("Splendor", "strategy", 2, 4);
    mockMvc
        .perform(
            put("/api/v1/games/{id}", id)
                .with(oidcLogin())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(drop.toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("copies"));
  }

  private String createGame(ObjectNode payload) throws Exception {
    var response = create(payload).andExpect(status().isCreated()).andReturn();

    return json.readTree(response.getResponse().getContentAsString()).get("id").asText();
  }

  private ResultActions create(ObjectNode payload) throws Exception {
    return mockMvc.perform(
        post("/api/v1/games")
            .with(oidcLogin())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload.toString()));
  }

  private ObjectNode payload(String title, String category, int minPlayers, int maxPlayers) {
    var payload = json.createObjectNode();
    payload.put("title", title);
    payload.put("category", category);
    payload.put("minPlayers", minPlayers);
    payload.put("maxPlayers", maxPlayers);

    return payload;
  }

  private ObjectNode payloadWithCopies(
      String title, String category, int minPlayers, int maxPlayers, UUID branchId, int copies) {
    var payload = payload(title, category, minPlayers, maxPlayers);
    copies(payload, branchId, copies);

    return payload;
  }

  private static void copies(ObjectNode payload, UUID branchId, int copies) {
    var entries =
        payload.has("copies") ? (ArrayNode) payload.get("copies") : payload.putArray("copies");
    var entry = entries.addObject();
    entry.put("branchId", branchId.toString());
    entry.put("copies", copies);
  }
}
