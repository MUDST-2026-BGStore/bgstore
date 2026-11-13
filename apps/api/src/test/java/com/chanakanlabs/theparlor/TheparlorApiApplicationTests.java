package com.chanakanlabs.theparlor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Tag("integration")
@AutoConfigureMetrics
@Testcontainers
@Import(TheparlorApiApplicationTests.AdminAccessProbe.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class TheparlorApiApplicationTests {
  static final String SECRET = UUID.randomUUID().toString();
  static final String PASSWORD = UUID.randomUUID().toString();
  static final int TEST_PORT = availablePort();
  static final ObjectMapper JSON = new ObjectMapper();

  @Container
  static GenericContainer<?> postgres =
      new GenericContainer<>("postgres:18.3")
          .withEnv("POSTGRES_DB", "theparlor")
          .withEnv("POSTGRES_USER", "theparlor")
          .withEnv("POSTGRES_PASSWORD", PASSWORD)
          .withExposedPorts(5432)
          .waitingFor(
              org.testcontainers.containers.wait.strategy.Wait.forLogMessage(
                  ".*database system is ready to accept connections.*\\n", 2));

  @Container
  static GenericContainer<?> valkey =
      new GenericContainer<>("valkey/valkey:9.1.2")
          .withExposedPorts(6379)
          .withCommand("valkey-server", "--requirepass", PASSWORD);

  @Container
  static KeycloakContainer keycloak =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.8.1")
          .withEnv("THEPARLOR_OIDC_CLIENT_SECRET", SECRET)
          .withRealmImportFile("realm-theparlor.json");

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;

  // Exercise the real admin security rule without requiring a production admin controller.
  @RestController
  static class AdminAccessProbe {
    @GetMapping("/api/v1/admin/test-access")
    String access() {
      return "ok";
    }
  }

  static int availablePort() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("server.port", () -> TEST_PORT);
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://"
                + postgres.getHost()
                + ":"
                + postgres.getMappedPort(5432)
                + "/theparlor");
    registry.add("spring.datasource.username", () -> "theparlor");
    registry.add("spring.datasource.password", () -> PASSWORD);
    registry.add("theparlor.auth.issuer", () -> keycloak.getAuthServerUrl() + "/realms/theparlor");
    registry.add(
        "theparlor.auth.backchannel-issuer",
        () -> keycloak.getAuthServerUrl() + "/realms/theparlor");
    registry.add("theparlor.auth.client-secret", () -> SECRET);
    registry.add("theparlor.auth.public-origin", () -> "http://localhost:" + TEST_PORT);
    registry.add("server.servlet.session.cookie.secure", () -> "false");
    registry.add("spring.data.redis.host", valkey::getHost);
    registry.add("spring.data.redis.port", () -> valkey.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> PASSWORD);
    registry.add("management.tracing.sampling.probability", () -> "0");
    registry.add("theparlor.payments.gateway", () -> "bogus");
  }

  @BeforeAll
  static void provisionLoginUsers() {
    var realm = keycloak.getKeycloakAdminClient().realm("theparlor");
    var client = realm.clients().findByClientId("theparlor-bff").getFirst();
    client.setSecret(SECRET);
    client.setRedirectUris(
        List.of("http://localhost:" + TEST_PORT + "/api/auth/callback/keycloak"));
    client.setAttributes(new java.util.HashMap<>(client.getAttributes()));
    client.getAttributes().put("post.logout.redirect.uris", "http://localhost:" + TEST_PORT + "/");
    realm.clients().get(client.getId()).update(client);
    // "customer" has no realm role: an ordinary signed-in shopper.
    for (String role : List.of("staff", "manager", "admin", "customer")) {
      var credential = new CredentialRepresentation();
      credential.setType("password");
      credential.setValue(PASSWORD);
      credential.setTemporary(false);
      var user = new UserRepresentation();
      user.setUsername("integration-" + role);
      user.setEnabled(true);
      user.setFirstName("Integration");
      user.setLastName(role);
      user.setEmail(role + "@example.invalid");
      user.setEmailVerified(true);
      user.setCredentials(List.of(credential));
      try (var response = realm.users().create(user)) {
        assertThat(response.getStatus()).isEqualTo(201);
      }
      var created = realm.users().searchByUsername(user.getUsername(), true).getFirst();
      if (!role.equals("customer"))
        realm
            .users()
            .get(created.getId())
            .roles()
            .realmLevel()
            .add(List.of(realm.roles().get(role).toRepresentation()));
    }
  }

  HttpClient browser() {
    return HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .build();
  }

  HttpResponse<String> get(HttpClient browser, String path) throws Exception {
    return browser.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpClient login(String role) throws Exception {
    var browser = browser();
    var start = get(browser, "/api/auth/login/keycloak");
    assertThat(start.statusCode()).isEqualTo(302);
    String authorizationUrl = start.headers().firstValue("location").orElseThrow();
    assertThat(authorizationUrl).contains("code_challenge_method=S256", "client_id=theparlor-bff");
    var page =
        browser.send(
            HttpRequest.newBuilder(URI.create(authorizationUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    var matcher = Pattern.compile("<form[^>]+action=\"([^\"]+)\"").matcher(page.body());
    assertThat(matcher.find()).as("Keycloak rendered a login form").isTrue();
    URI action = URI.create(matcher.group(1).replace("&amp;", "&"));
    // Emulate localhost browser cookies: RFC 6265 format and localhost Secure-cookie exception.
    ((CookieManager) browser.cookieHandler().orElseThrow())
        .getCookieStore()
        .getCookies()
        .forEach(
            cookie -> {
              cookie.setVersion(0);
              cookie.setSecure(false);
            });
    var credentials =
        browser.send(
            HttpRequest.newBuilder(action)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=integration-"
                            + role
                            + "&password="
                            + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(credentials.statusCode())
        .as(
            "Keycloak login: %s",
            credentials.body().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " "))
        .isEqualTo(302);
    var callback =
        browser.send(
            HttpRequest.newBuilder(
                    URI.create(credentials.headers().firstValue("location").orElseThrow()))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(callback.statusCode()).isEqualTo(302);
    assertThat(callback.headers().firstValue("location").orElseThrow())
        .isEqualTo("http://localhost:" + port + "/login");
    assertThat(callback.headers().allValues("set-cookie").toString())
        .contains("THEPARLOR_SESSION", "HttpOnly", "SameSite=Lax");
    return browser;
  }

  HttpResponse<String> writeJson(HttpClient client, String method, String path, String body)
      throws Exception {
    var csrf = JSON.readTree(get(client, "/api/auth/csrf").body());
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header(csrf.get("headerName").asString(), csrf.get("token").asString())
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void catalogWritesRequireManagerAndPublicReadersSeeTranslationsAndSearch() throws Exception {
    String key = "Category-" + UUID.randomUUID();
    String categoryBody =
        "{\"name\":\"" + key + "\",\"names\":{\"en-US\":\"English\",\"th-TH\":\"Thai\"}}";
    assertThat(writeJson(browser(), "POST", "/api/v1/categories", categoryBody).statusCode())
        .isEqualTo(401);
    assertThat(writeJson(login("staff"), "POST", "/api/v1/categories", categoryBody).statusCode())
        .isEqualTo(403);
    var manager = login("manager");
    var response = writeJson(manager, "POST", "/api/v1/categories", categoryBody);
    assertThat(response.statusCode()).isEqualTo(201);
    String categoryId = JSON.readTree(response.body()).get("id").asText();
    assertThat(get(browser(), "/api/v1/categories/" + categoryId).body()).contains("Thai");
    var duplicate = writeJson(manager, "POST", "/api/v1/categories", categoryBody);
    assertThat(duplicate.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(duplicate.body()).get("code").asString())
        .isEqualTo("CATALOG_CONFLICT");
    assertThat(
            writeJson(
                    manager,
                    "PATCH",
                    "/api/v1/categories/" + categoryId,
                    "{\"names\":{\"en-US\":\"Updated\"}}")
                .statusCode())
        .isEqualTo(200);
    assertThat(get(browser(), "/api/v1/categories/" + categoryId).body())
        .contains("Updated")
        .doesNotContain("Thai");
    assertThat(get(browser(), "/api/v1/categories?limit=1&cursor=invalid").statusCode())
        .isEqualTo(400);
    assertThat(get(browser(), "/api/v1/categories?limit=101").statusCode()).isEqualTo(400);

    String title = "Search-" + UUID.randomUUID();
    String create =
        "{\"name\":\""
            + title
            + "\",\"minPlayers\":1,\"maxPlayers\":4,\"playMinutesMin\":10,\"playMinutesMax\":20,\"active\":true,\"categories\":[\""
            + key
            + "\"],\"descriptions\":{\"en-US\":\"Description\"},\"images\":[{\"url\":\"https://assets/one\"}]}";
    response = writeJson(manager, "POST", "/api/v1/games", create);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    String gameId = JSON.readTree(response.body()).get("id").asText();
    response =
        writeJson(
            manager, "PATCH", "/api/v1/games/" + gameId, "{\"name\":\"" + title + " renamed\"}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(JSON.readTree(response.body()).get("images").size()).isEqualTo(1);
    assertThat(JSON.readTree(response.body()).get("descriptions").get("en-US").asString())
        .isEqualTo("Description");
    assertThat(get(browser(), "/api/v1/games?q=" + title + "&players=2").body()).contains(gameId);
    assertThat(JSON.readTree(get(browser(), "/api/v1/games?q=" + title).body()).has("nextCursor"))
        .isFalse();
    assertThat(
            writeJson(manager, "PATCH", "/api/v1/games/" + gameId, "{\"images\":[null]}")
                .statusCode())
        .isEqualTo(400);

    assertThat(writeJson(manager, "PATCH", "/api/v1/games/" + gameId, "{}").statusCode())
        .isEqualTo(400);
    assertThat(
            writeJson(manager, "PATCH", "/api/v1/games/" + gameId, "{\"images\":[]}").statusCode())
        .isEqualTo(200);
    assertThat(JSON.readTree(get(browser(), "/api/v1/games/" + gameId).body()).get("images").size())
        .isZero();

    response =
        writeJson(
            manager,
            "POST",
            "/api/v1/games/" + gameId + "/editions",
            "{\"editionKind\":\"BASE_GAME\",\"languageCode\":\"en\",\"active\":false}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    String editionId = JSON.readTree(response.body()).get("id").asText();
    assertThat(get(browser(), "/api/v1/games/" + gameId + "/editions/" + editionId).statusCode())
        .isEqualTo(404);
    assertThat(get(manager, "/api/v1/games/" + gameId + "/editions/" + editionId).statusCode())
        .isEqualTo(200);
    assertThat(get(browser(), "/api/v1/games/" + gameId + "/editions?active=false").statusCode())
        .isEqualTo(404);
    assertThat(
            writeJson(manager, "PATCH", "/api/v1/games/" + gameId, "{\"active\":false}")
                .statusCode())
        .isEqualTo(200);
    assertThat(get(browser(), "/api/v1/games/" + gameId).statusCode()).isEqualTo(404);
  }

  @Test
  void saleStockChangesRequireBranchAccessAndPreserveHeldUnits() throws Exception {
    var branch = UUID.randomUUID();
    var game = UUID.randomUUID();
    var edition = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO branches(id,name,address) VALUES (?, 'Inventory test', 'Address')", branch);
    jdbc.update(
        "INSERT INTO games(id,name,min_players,max_players,play_minutes_min,play_minutes_max) VALUES (?, 'Inventory game',1,4,10,20)",
        game);
    jdbc.update(
        "INSERT INTO game_descriptions(game_id,locale,description) VALUES (?, 'en-US', 'Game')",
        game);
    jdbc.update(
        "INSERT INTO game_editions(id,game_id,kind,language_code) VALUES (?,?,'BASE_GAME','en')",
        edition,
        game);
    jdbc.update(
        "INSERT INTO branch_games(branch_id,game_id,sale_enabled,rental_enabled,staff_can_teach,rental_hourly_rate_minor) VALUES (?,?,true,false,false,0)",
        branch,
        game);
    jdbc.update(
        "INSERT INTO branch_editions(branch_id,edition_id,sale_price_minor,stock_on_hand,stock_held) VALUES (?,?,10000,5,2)",
        branch,
        edition);
    String path = "/api/v1/branches/" + branch + "/editions/" + edition;
    assertThat(
            writeJson(
                    login("staff"),
                    "POST",
                    path + "/stock-adjustments",
                    "{\"delta\":1,\"reason\":\"Receipt\"}")
                .statusCode())
        .isEqualTo(403);
    var manager = login("manager");
    assertThat(
            writeJson(manager, "PUT", path, "{\"salePrice\":{\"amountMinor\":12000}}").statusCode())
        .isEqualTo(200);
    var response =
        writeJson(
            manager,
            "POST",
            path + "/stock-adjustments",
            "{\"delta\":-4,\"reason\":\"Correction\"}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
    response =
        writeJson(
            manager, "POST", path + "/stock-adjustments", "{\"delta\":3,\"reason\":\"Receipt\"}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    assertThat(JSON.readTree(response.body()).get("saleOnHand").asInt()).isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "SELECT sale_price_minor FROM branch_editions WHERE branch_id=? AND edition_id=?",
                Long.class,
                branch,
                edition))
        .isEqualTo(12000);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sale_stock_adjustments WHERE branch_id=? AND edition_id=?",
                Integer.class,
                branch,
                edition))
        .isEqualTo(1);
    String copyPath = "/api/v1/branches/" + branch + "/games/" + game + "/rental-copies";
    String code = "x".repeat(80);
    var createdCopy = writeJson(manager, "POST", copyPath, "{\"assetCode\":\"" + code + "\"}");
    assertThat(createdCopy.statusCode()).as(createdCopy.body()).isEqualTo(201);
    assertThat(JSON.readTree(createdCopy.body()).get("state").asString()).isEqualTo("AVAILABLE");
    assertThat(
            writeJson(manager, "POST", copyPath, "{\"assetCode\":\"" + code + "\"}").statusCode())
        .isEqualTo(409);
  }

  @Test
  void branchesAndTablesAreAdministeredPubliclyReadableAndGuardCommitments() throws Exception {
    String hours =
        "[{\"dayOfWeek\":\"MONDAY\",\"opensAt\":\"10:00\",\"closesAt\":\"24:00\"},"
            + "{\"dayOfWeek\":\"TUESDAY\",\"opensAt\":\"00:00\",\"closesAt\":\"02:00\"}]";
    String body =
        "{\"name\":\"Branch API test\",\"address\":\"1 Test Road\",\"phone\":\"+6620000000\","
            + "\"openingHours\":"
            + hours
            + ",\"active\":true}";
    assertThat(writeJson(login("manager"), "POST", "/api/v1/branches", body).statusCode())
        .isEqualTo(403);
    var admin = login("admin");
    var created = writeJson(admin, "POST", "/api/v1/branches", body);
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    String branch = JSON.readTree(created.body()).get("id").asString();
    // PostgreSQL keeps the end of day as 24:00:00, never as midnight.
    assertThat(
            jdbc.queryForObject(
                "SELECT closes_at::text FROM branch_opening_hours WHERE branch_id=?::uuid AND day_of_week=1",
                String.class,
                branch))
        .isEqualTo("24:00:00");

    var anonymous = browser();
    var read = get(anonymous, "/api/v1/branches/" + branch);
    assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
    var periods = JSON.readTree(read.body()).get("openingHours");
    assertThat(periods.get(0).get("closesAt").asString()).isEqualTo("24:00");
    assertThat(periods.get(1).get("opensAt").asString()).isEqualTo("00:00");
    assertThat(JSON.readTree(read.body()).get("phone").asString()).isEqualTo("+6620000000");
    var firstPage = get(anonymous, "/api/v1/branches?limit=1");
    assertThat(firstPage.statusCode()).as(firstPage.body()).isEqualTo(200);
    assertThat(JSON.readTree(firstPage.body()).get("items")).hasSize(1);
    var invalidCursor = get(anonymous, "/api/v1/branches?cursor=bm9wZQ");
    assertThat(invalidCursor.statusCode()).isEqualTo(400);
    assertThat(JSON.readTree(invalidCursor.body()).get("code").asString())
        .isEqualTo("INVALID_REQUEST");

    var overlap =
        writeJson(
            admin,
            "PATCH",
            "/api/v1/branches/" + branch,
            "{\"openingHours\":[{\"dayOfWeek\":\"MONDAY\",\"opensAt\":\"10:00\",\"closesAt\":\"14:00\"},"
                + "{\"dayOfWeek\":\"MONDAY\",\"opensAt\":\"13:00\",\"closesAt\":\"18:00\"}]}");
    assertThat(overlap.statusCode()).as(overlap.body()).isEqualTo(400);

    String tables = "/api/v1/branches/" + branch + "/tables";
    String tableBody =
        "{\"label\":\"T1\",\"capacity\":4,\"hourlyRate\":{\"amountMinor\":5000},"
            + "\"operationalStatus\":\"IN_SERVICE\"}";
    assertThat(writeJson(login("staff"), "POST", tables, tableBody).statusCode()).isEqualTo(403);
    var manager = login("manager");
    var table = writeJson(manager, "POST", tables, tableBody);
    assertThat(table.statusCode()).as(table.body()).isEqualTo(201);
    String tableId = JSON.readTree(table.body()).get("id").asString();
    assertThat(JSON.readTree(table.body()).get("state").asString()).isEqualTo("AVAILABLE");
    var duplicate = writeJson(manager, "POST", tables, tableBody);
    assertThat(duplicate.statusCode()).as(duplicate.body()).isEqualTo(409);
    assertThat(JSON.readTree(duplicate.body()).get("code").asString()).isEqualTo("CONFLICT");

    // A confirmed booking covering now, inside Monday 10:00 through Tuesday 02:00 opening time.
    jdbc.update(
        "INSERT INTO reservations(branch_id,guest_name,source,status,starts_at,ends_at,party_size,"
            + "table_id,table_hourly_rate_minor,created_by_subject) "
            + "VALUES (?::uuid,'Guest','WALK_IN','CONFIRMED',now() - interval '1 hour',"
            + "now() + interval '1 hour',3,?::uuid,5000,'test')",
        branch,
        tableId);
    var listed = get(anonymous, tables);
    assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
    assertThat(JSON.readTree(listed.body()).get("items").get(0).get("state").asString())
        .isEqualTo("RESERVED");
    String tablePath = tables + "/" + tableId;
    assertThat(writeJson(manager, "PATCH", tablePath, "{\"capacity\":2}").statusCode())
        .isEqualTo(409);
    assertThat(
            writeJson(manager, "PATCH", tablePath, "{\"operationalStatus\":\"OUT_OF_SERVICE\"}")
                .statusCode())
        .isEqualTo(409);
    assertThat(writeJson(manager, "PATCH", tablePath, "{\"capacity\":3}").statusCode())
        .isEqualTo(200);
    assertThat(
            writeJson(admin, "PATCH", "/api/v1/branches/" + branch, "{\"active\":false}")
                .statusCode())
        .isEqualTo(409);
    assertThat(
            writeJson(admin, "PATCH", "/api/v1/branches/" + branch, "{\"openingHours\":[]}")
                .statusCode())
        .isEqualTo(409);

    jdbc.update(
        "UPDATE reservations SET status='CHECKED_IN', checked_in_at=now() WHERE table_id=?::uuid",
        tableId);
    assertThat(JSON.readTree(get(anonymous, tablePath).body()).get("state").asString())
        .isEqualTo("OCCUPIED");
    jdbc.update(
        "UPDATE reservations SET status='CANCELLED', checked_in_at=NULL, cancelled_at=now() "
            + "WHERE table_id=?::uuid",
        tableId);

    var closed = writeJson(admin, "PATCH", "/api/v1/branches/" + branch, "{\"active\":false}");
    assertThat(closed.statusCode()).as(closed.body()).isEqualTo(200);
    assertThat(get(anonymous, "/api/v1/branches/" + branch).statusCode()).isEqualTo(404);
    assertThat(get(anonymous, tables).statusCode()).isEqualTo(404);
    assertThat(get(admin, "/api/v1/branches/" + branch).statusCode()).isEqualTo(200);
    // Operational writes at an inactive branch conflict, even for managers.
    assertThat(writeJson(manager, "PATCH", tablePath, "{\"capacity\":6}").statusCode())
        .isEqualTo(409);
  }

  @Test
  void branchOfferingsArePublicAndCopyConditionChangesMoveCommitments() throws Exception {
    var branch = UUID.randomUUID();
    var game = UUID.randomUUID();
    var hidden = UUID.randomUUID();
    var edition = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO branches(id,name,address) VALUES (?, 'Offer test', 'Address')", branch);
    for (var entry : java.util.Map.of(game, "Offered Meeple", hidden, "Hidden Meeple").entrySet()) {
      jdbc.update(
          "INSERT INTO games(id,name,min_players,max_players,play_minutes_min,play_minutes_max,active) VALUES (?,?,1,4,10,20,?)",
          entry.getKey(),
          entry.getValue(),
          entry.getKey().equals(game));
      jdbc.update(
          "INSERT INTO game_descriptions(game_id,locale,description) VALUES (?, 'en-US', 'Game')",
          entry.getKey());
    }
    jdbc.update(
        "INSERT INTO game_editions(id,game_id,kind,language_code) VALUES (?,?,'BASE_GAME','en')",
        edition,
        game);
    jdbc.update("INSERT INTO branch_games(branch_id,game_id) VALUES (?,?)", branch, hidden);

    String games = "/api/v1/branches/" + branch + "/games";
    String offer =
        "{\"saleEnabled\":true,\"rentalEnabled\":true,\"staffCanTeach\":true,"
            + "\"rentalHourlyRate\":{\"amountMinor\":4000}}";
    assertThat(writeJson(login("staff"), "PUT", games + "/" + game, offer).statusCode())
        .isEqualTo(403);
    var manager = login("manager");
    var configured = writeJson(manager, "PUT", games + "/" + game, offer);
    assertThat(configured.statusCode()).as(configured.body()).isEqualTo(200);
    assertThat(writeJson(manager, "PUT", games + "/" + hidden, offer).statusCode()).isEqualTo(404);
    assertThat(
            writeJson(
                    manager,
                    "PUT",
                    "/api/v1/branches/" + branch + "/editions/" + edition,
                    "{\"salePrice\":{\"amountMinor\":89000}}")
                .statusCode())
        .isEqualTo(200);
    assertThat(
            writeJson(
                    manager,
                    "POST",
                    "/api/v1/branches/" + branch + "/editions/" + edition + "/stock-adjustments",
                    "{\"delta\":3,\"reason\":\"Receipt\"}")
                .statusCode())
        .isEqualTo(201);
    String copies = games + "/" + game + "/rental-copies";
    var copyIds = new java.util.ArrayList<String>();
    for (String code : List.of("A", "B")) {
      var created = writeJson(manager, "POST", copies, "{\"assetCode\":\"" + code + "\"}");
      assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
      copyIds.add(JSON.readTree(created.body()).get("id").asString());
    }

    var anonymous = browser();
    var read = get(anonymous, games + "/" + game);
    assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
    var offering = JSON.readTree(read.body());
    assertThat(offering.get("game").get("name").asString()).isEqualTo("Offered Meeple");
    assertThat(offering.get("rentalHourlyRate").get("amountMinor").asLong()).isEqualTo(4000);
    assertThat(offering.get("editions").get(0).get("saleAvailable").asInt()).isEqualTo(3);
    assertThat(offering.get("rentalAvailableNow").asInt()).isEqualTo(2);
    assertThat(get(anonymous, games + "/" + hidden).statusCode()).isEqualTo(404);
    // Inactive catalog titles never appear, and name search is case-insensitive.
    var listed = JSON.readTree(get(anonymous, games + "?q=mEEple").body()).get("items");
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).get("game").get("id").asString()).isEqualTo(game.toString());
    assertThat(JSON.readTree(get(anonymous, games + "?q=zzz").body()).get("items")).isEmpty();
    assertThat(get(anonymous, copies).statusCode()).isEqualTo(401);

    // Copy A is out with an active rental; copy B is free.
    var table = UUID.randomUUID();
    var reservation = UUID.randomUUID();
    var rental = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO branch_tables(id,branch_id,label,seats) VALUES (?,?,'T1',4)", table, branch);
    jdbc.update(
        "INSERT INTO reservations(id,branch_id,guest_name,source,status,starts_at,ends_at,party_size,"
            + "table_id,table_hourly_rate_minor,created_by_subject,checked_in_at) "
            + "VALUES (?,?,'Guest','WALK_IN','CHECKED_IN',now() - interval '1 hour',"
            + "now() + interval '1 hour',2,?,5000,'test',now())",
        reservation,
        branch,
        table);
    jdbc.update(
        "INSERT INTO rentals(id,branch_id,reservation_id,started_at,planned_ends_at,checked_in_by) "
            + "VALUES (?,?,?,now() - interval '1 hour',now() + interval '1 hour','test')",
        rental,
        branch,
        reservation);
    jdbc.update(
        "INSERT INTO rental_copy_allocations(rental_id,branch_id,copy_id) VALUES (?,?,?::uuid)",
        rental,
        branch,
        copyIds.get(0));
    assertThat(
            JSON.readTree(get(anonymous, games + "/" + game).body())
                .get("rentalAvailableNow")
                .asInt())
        .isEqualTo(1);

    String copyA = copies + "/" + copyIds.get(0);
    String copyB = copies + "/" + copyIds.get(1);
    assertThat(
            writeJson(manager, "PATCH", copyA, "{\"operationalStatus\":\"OUT_OF_SERVICE\"}")
                .statusCode())
        .isEqualTo(400);
    var damaged =
        writeJson(
            manager,
            "PATCH",
            copyA,
            "{\"operationalStatus\":\"OUT_OF_SERVICE\",\"reason\":\"Missing pieces\"}");
    assertThat(damaged.statusCode()).as(damaged.body()).isEqualTo(200);
    assertThat(JSON.readTree(damaged.body()).get("reason").asString()).isEqualTo("Missing pieces");
    // The rental now uses copy B, so B cannot leave the pool: nothing else can cover it.
    assertThat(
            jdbc.queryForObject(
                "SELECT copy_id::text FROM rental_copy_allocations WHERE rental_id=?",
                String.class,
                rental))
        .isEqualTo(copyIds.get(1));
    var stranded =
        writeJson(
            manager, "PATCH", copyB, "{\"operationalStatus\":\"RETIRED\",\"reason\":\"Lost\"}");
    assertThat(stranded.statusCode()).as(stranded.body()).isEqualTo(409);

    var page = JSON.readTree(get(manager, copies + "?limit=1").body());
    assertThat(page.get("items").get(0).get("state").asString()).isEqualTo("OUT_OF_SERVICE");
    assertThat(page.get("items").get(0).get("reason").asString()).isEqualTo("Missing pieces");
    var rest =
        JSON.readTree(get(manager, copies + "?cursor=" + page.get("nextCursor").asString()).body());
    assertThat(rest.get("items").get(0).get("state").asString()).isEqualTo("IN_USE");

    assertThat(
            writeJson(
                    manager,
                    "PATCH",
                    copyA,
                    "{\"operationalStatus\":\"RETIRED\",\"reason\":\"Scrapped\"}")
                .statusCode())
        .isEqualTo(200);
    assertThat(
            writeJson(manager, "PATCH", copyA, "{\"operationalStatus\":\"USABLE\"}").statusCode())
        .isEqualTo(409);
    assertThat(
            jdbc.queryForList(
                "SELECT to_status FROM rental_copy_condition_changes WHERE copy_id=?::uuid ORDER BY created_at, id",
                String.class,
                copyIds.get(0)))
        .containsExactly("OUT_OF_SERVICE", "RETIRED");
    var counts = JSON.readTree(get(anonymous, games + "/" + game).body());
    assertThat(counts.get("rentalTotal").asInt()).isEqualTo(1);
    assertThat(counts.get("rentalAvailableNow").asInt()).isZero();
  }

  HttpResponse<String> send(
      HttpClient client, String method, String path, String body, String... headers)
      throws Exception {
    var csrf = JSON.readTree(get(client, "/api/auth/csrf").body());
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header(csrf.get("headerName").asString(), csrf.get("token").asString())
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    for (int i = 0; i < headers.length; i += 2) request.header(headers[i], headers[i + 1]);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  String paymentToken(HttpClient client, String cardNumber) throws Exception {
    var response =
        send(
            client,
            "POST",
            "/api/v1/payments/tokens",
            "{\"number\":\""
                + cardNumber
                + "\",\"expMonth\":12,\"expYear\":2999,\"cvc\":\"123\",\"holderName\":\"Test\"}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    assertThat(response.body()).doesNotContain(cardNumber);
    return JSON.readTree(response.body()).get("token").asString();
  }

  int stock(String column, UUID branch, UUID edition) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM branch_editions WHERE branch_id=? AND edition_id=?",
        Integer.class,
        branch,
        edition);
  }

  @Test
  void guestsAndCustomersBuyThroughCartsCardPaymentsAndStaffFulfillment() throws Exception {
    var branch = UUID.randomUUID();
    var game = UUID.randomUUID();
    var edition = UUID.randomUUID();
    jdbc.update("UPDATE branches SET fulfills_delivery=false");
    jdbc.update(
        "INSERT INTO branches(id,name,address,fulfills_delivery) VALUES (?, 'Retail test', 'Address', true)",
        branch);
    jdbc.update(
        "INSERT INTO games(id,name,min_players,max_players,play_minutes_min,play_minutes_max) VALUES (?, 'Retail game',1,4,10,20)",
        game);
    jdbc.update(
        "INSERT INTO game_descriptions(game_id,locale,description) VALUES (?, 'en-US', 'Game')",
        game);
    jdbc.update(
        "INSERT INTO game_editions(id,game_id,kind,language_code) VALUES (?,?,'BASE_GAME','th')",
        edition,
        game);
    jdbc.update(
        "INSERT INTO branch_games(branch_id,game_id,sale_enabled) VALUES (?,?,true)", branch, game);
    jdbc.update(
        "INSERT INTO branch_editions(branch_id,edition_id,sale_price_minor,stock_on_hand) VALUES (?,?,89000,5)",
        branch,
        edition);

    // A guest opens a cart; a retry returns the same cart with a fresh token.
    var guest = browser();
    String cartKey = UUID.randomUUID().toString();
    var opened = send(guest, "POST", "/api/v1/carts", null, "Idempotency-Key", cartKey);
    assertThat(opened.statusCode()).as(opened.body()).isEqualTo(201);
    String cartId = JSON.readTree(opened.body()).get("id").asString();
    String staleToken = JSON.readTree(opened.body()).get("token").asString();
    var reopened = send(guest, "POST", "/api/v1/carts", null, "Idempotency-Key", cartKey);
    assertThat(JSON.readTree(reopened.body()).get("id").asString()).isEqualTo(cartId);
    String token = JSON.readTree(reopened.body()).get("token").asString();
    assertThat(token).isNotEqualTo(staleToken).hasSizeGreaterThanOrEqualTo(32);
    String cart = "/api/v1/carts/" + cartId;
    assertThat(get(guest, cart).statusCode()).isEqualTo(404);
    assertThat(send(guest, "GET", cart, null, "X-Cart-Token", staleToken).statusCode())
        .isEqualTo(404);
    assertThat(send(browser(), "GET", cart, null, "X-Cart-Token", token).statusCode())
        .isEqualTo(200);

    String line = "{\"editionId\":\"" + edition + "\",\"quantity\":2}";
    var added = send(guest, "POST", cart + "/items", line, "X-Cart-Token", token);
    assertThat(added.statusCode()).as(added.body()).isEqualTo(200);
    var cartView = JSON.readTree(added.body());
    assertThat(cartView.get("subtotal").asLong()).isEqualTo(178000);
    assertThat(cartView.get("items").get(0).get("gameName").asString()).isEqualTo("Retail game");
    assertThat(cartView.get("items").get(0).get("gameId").asString()).isEqualTo(game.toString());
    assertThat(cartView.get("items").get(0).get("available").asInt()).isEqualTo(5);
    assertThat(cartView.get("checkoutReady").asBoolean()).isTrue();
    // Adding never exceeds stock, but an explicit quantity is kept and flagged.
    assertThat(
            send(
                    guest,
                    "POST",
                    cart + "/items",
                    "{\"editionId\":\"" + edition + "\",\"quantity\":4}",
                    "X-Cart-Token",
                    token)
                .statusCode())
        .isEqualTo(409);
    String linePath = cart + "/items/" + edition;
    var over = send(guest, "PATCH", linePath, "{\"quantity\":9}", "X-Cart-Token", token);
    assertThat(JSON.readTree(over.body()).get("issues").get(0).get("code").asString())
        .isEqualTo("INSUFFICIENT_STOCK");
    assertThat(JSON.readTree(over.body()).get("checkoutReady").asBoolean()).isFalse();
    assertThat(
            send(guest, "PATCH", linePath, "{\"quantity\":2}", "X-Cart-Token", token).statusCode())
        .isEqualTo(200);
    assertThat(stock("stock_held", branch, edition)).isZero();

    // Card rules run before any gateway call.
    var badCard =
        send(
            guest,
            "POST",
            "/api/v1/payments/tokens",
            "{\"number\":\"4242424242424241\",\"expMonth\":12,\"expYear\":2999,\"cvc\":\"123\",\"holderName\":\"Test\"}");
    assertThat(badCard.statusCode()).isEqualTo(400);
    assertThat(badCard.body()).doesNotContain("4242424242424241");

    String checkout = cart + "/checkout";
    String details =
        "\"contact\":{\"name\":\"Guest Buyer\",\"email\":\"guest@example.invalid\",\"phone\":\"+66812345678\"},"
            + "\"shippingAddress\":{\"recipientName\":\"Guest Buyer\",\"line1\":\"1 Test Road\","
            + "\"district\":\"Pathum Wan\",\"province\":\"Bangkok\",\"postalCode\":\"10330\"}";
    // A declined card places nothing and holds nothing; the cart stays open.
    var declined =
        send(
            guest,
            "POST",
            checkout,
            "{"
                + details
                + ",\"paymentToken\":\""
                + paymentToken(guest, "4000000000000002")
                + "\"}",
            "X-Cart-Token",
            token,
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(declined.statusCode()).as(declined.body()).isEqualTo(402);
    assertThat(JSON.readTree(declined.body()).get("code").asString()).isEqualTo("PAYMENT_DECLINED");
    assertThat(stock("stock_held", branch, edition)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE branch_id=?", Integer.class, branch))
        .isZero();
    var providerDown =
        send(
            guest,
            "POST",
            checkout,
            "{"
                + details
                + ",\"paymentToken\":\""
                + paymentToken(guest, "4000000000000119")
                + "\"}",
            "X-Cart-Token",
            token,
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(providerDown.statusCode()).as(providerDown.body()).isEqualTo(502);

    String goodToken = paymentToken(guest, "4242 4242 4242 4242");
    // A delivery cart needs an address.
    assertThat(
            send(
                    guest,
                    "POST",
                    checkout,
                    "{\"contact\":{\"name\":\"Guest Buyer\",\"email\":\"guest@example.invalid\",\"phone\":\"+66812345678\"},\"paymentToken\":\""
                        + goodToken
                        + "\"}",
                    "X-Cart-Token",
                    token,
                    "Idempotency-Key",
                    UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(400);
    String orderKey = UUID.randomUUID().toString();
    String orderBody = "{" + details + ",\"paymentToken\":\"" + goodToken + "\"}";
    var placed =
        send(
            guest, "POST", checkout, orderBody, "X-Cart-Token", token, "Idempotency-Key", orderKey);
    assertThat(placed.statusCode()).as(placed.body()).isEqualTo(201);
    var order = JSON.readTree(placed.body());
    String orderId = order.get("id").asString();
    assertThat(order.get("status").asString()).isEqualTo("PLACED");
    assertThat(order.get("fulfillmentMethod").asString()).isEqualTo("SHIPPING");
    assertThat(order.get("branchId").asString()).isEqualTo(branch.toString());
    assertThat(order.get("total").get("amountMinor").asLong()).isEqualTo(178000);
    assertThat(order.get("customer").get("kind").asString()).isEqualTo("GUEST");
    assertThat(order.get("items").get(0).get("editionLanguage").asString()).isEqualTo("th");
    assertThat(order.get("items").get(0).get("gameId").asString()).isEqualTo(game.toString());
    assertThat(order.get("shippingAddress").get("postalCode").asString()).isEqualTo("10330");
    assertThat(order.get("payment").get("status").asString()).isEqualTo("AUTHORIZED");
    assertThat(order.get("payment").get("cardBrand").asString()).isEqualTo("VISA");
    assertThat(order.get("payment").get("cardLast4").asString()).isEqualTo("4242");
    assertThat(stock("stock_held", branch, edition)).isEqualTo(2);
    // The same key and body replay the order; a different body conflicts; the cart is spent.
    var replay =
        send(
            guest, "POST", checkout, orderBody, "X-Cart-Token", token, "Idempotency-Key", orderKey);
    assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);
    assertThat(JSON.readTree(replay.body()).get("id").asString()).isEqualTo(orderId);
    assertThat(
            send(
                    guest,
                    "POST",
                    checkout,
                    orderBody.replace("Guest Buyer", "Someone Else"),
                    "X-Cart-Token",
                    token,
                    "Idempotency-Key",
                    orderKey)
                .statusCode())
        .isEqualTo(409);
    assertThat(send(guest, "GET", cart, null, "X-Cart-Token", token).statusCode()).isEqualTo(409);
    assertThat(stock("stock_held", branch, edition)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.branch_id=?",
                Integer.class,
                branch))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM bogus_payment_tokens WHERE card_last4 NOT IN ('4242','0002','0119')",
                Integer.class))
        .isZero();

    // Staff hand the order over: stock is consumed and the payment captured, exactly once.
    String orders = "/api/v1/branches/" + branch + "/orders";
    assertThat(get(guest, orders + "/" + orderId).statusCode()).isEqualTo(401);
    assertThat(send(login("staff"), "POST", orders + "/" + orderId + "/fulfill", null).statusCode())
        .isEqualTo(403);
    var manager = login("manager");
    var listed = JSON.readTree(get(manager, orders + "?status=PLACED").body()).get("items");
    assertThat(listed).hasSize(1);
    for (int attempt = 0; attempt < 2; attempt++) {
      var fulfilled = send(manager, "POST", orders + "/" + orderId + "/fulfill", null);
      assertThat(fulfilled.statusCode()).as(fulfilled.body()).isEqualTo(200);
      assertThat(JSON.readTree(fulfilled.body()).get("status").asString()).isEqualTo("FULFILLED");
      assertThat(JSON.readTree(fulfilled.body()).get("payment").get("status").asString())
          .isEqualTo("CAPTURED");
    }
    assertThat(stock("stock_on_hand", branch, edition)).isEqualTo(3);
    assertThat(stock("stock_held", branch, edition)).isZero();
    assertThat(
            send(manager, "POST", orders + "/" + orderId + "/cancel", "{\"reason\":\"Too late\"}")
                .statusCode())
        .isEqualTo(409);

    // A signed-in customer: account cart, guest-cart merge, direct order and own cancellation.
    var customer = login("staff");
    var account =
        send(
            customer,
            "POST",
            "/api/v1/me",
            "{\"firstName\":\"Retail\",\"lastName\":\"Customer\",\"phoneNumber\":\"+66898765432\","
                + "\"preferences\":{\"preferredLocale\":\"en-US\"}}");
    assertThat(account.statusCode()).as(account.body()).isIn(200, 201);
    var second = browser();
    var guestCart =
        JSON.readTree(
            send(
                    second,
                    "POST",
                    "/api/v1/carts",
                    null,
                    "Idempotency-Key",
                    UUID.randomUUID().toString())
                .body());
    String guestToken = guestCart.get("token").asString();
    String guestPath = "/api/v1/carts/" + guestCart.get("id").asString();
    assertThat(
            send(second, "POST", guestPath + "/items", line, "X-Cart-Token", guestToken)
                .statusCode())
        .isEqualTo(200);
    var merged =
        send(
            customer,
            "POST",
            "/api/v1/me/cart/merge",
            "{\"cartId\":\""
                + guestCart.get("id").asString()
                + "\",\"cartToken\":\""
                + guestToken
                + "\"}");
    assertThat(merged.statusCode()).as(merged.body()).isEqualTo(200);
    assertThat(JSON.readTree(merged.body()).get("items").get(0).get("quantity").asInt())
        .isEqualTo(2);
    assertThat(send(second, "GET", guestPath, null, "X-Cart-Token", guestToken).statusCode())
        .isEqualTo(404);
    var mine = JSON.readTree(get(customer, "/api/v1/me/cart").body());
    assertThat(mine.get("id").asString())
        .isEqualTo(JSON.readTree(merged.body()).get("id").asString());
    // Another signed-in user cannot reach it, with or without a made-up token.
    assertThat(get(manager, "/api/v1/carts/" + mine.get("id").asString()).statusCode())
        .isEqualTo(404);

    String myKey = UUID.randomUUID().toString();
    String myOrder =
        "{\"items\":[{\"editionId\":\""
            + edition
            + "\",\"quantity\":1}],\"paymentToken\":\""
            + paymentToken(customer, "5555555555554444")
            + "\"}";
    var direct = send(customer, "POST", orders, myOrder, "Idempotency-Key", myKey);
    assertThat(direct.statusCode()).as(direct.body()).isEqualTo(201);
    var directOrder = JSON.readTree(direct.body());
    assertThat(directOrder.get("fulfillmentMethod").asString()).isEqualTo("PICKUP");
    assertThat(directOrder.get("customer").get("kind").asString()).isEqualTo("REGISTERED");
    assertThat(directOrder.get("contact").get("email").asString())
        .isEqualTo("staff@example.invalid");
    assertThat(directOrder.get("payment").get("cardBrand").asString()).isEqualTo("MASTERCARD");
    assertThat(stock("stock_held", branch, edition)).isEqualTo(1);
    String directPath = orders + "/" + directOrder.get("id").asString();
    assertThat(JSON.readTree(get(customer, "/api/v1/me/orders").body()).get("items")).hasSize(1);
    assertThat(get(customer, directPath).statusCode()).isEqualTo(200);
    // The guest's order is not this customer's, and they do not operate the branch.
    assertThat(get(customer, orders + "/" + orderId).statusCode()).isEqualTo(403);
    for (int attempt = 0; attempt < 2; attempt++) {
      var cancelled =
          send(customer, "POST", directPath + "/cancel", "{\"reason\":\"Changed my mind\"}");
      assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
      assertThat(JSON.readTree(cancelled.body()).get("status").asString()).isEqualTo("CANCELLED");
      assertThat(JSON.readTree(cancelled.body()).get("payment").get("status").asString())
          .isEqualTo("VOIDED");
    }
    assertThat(stock("stock_held", branch, edition)).isZero();
    assertThat(stock("stock_on_hand", branch, edition)).isEqualTo(3);

    // A counter sale never touches the gateway; a card that fails at capture blocks handover.
    var counter =
        send(
            manager,
            "POST",
            "/api/v1/branches/" + branch + "/staff/orders",
            "{\"customer\":{\"kind\":\"GUEST\",\"displayName\":\"Walk-in\"},\"items\":[{\"editionId\":\""
                + edition
                + "\",\"quantity\":1}]}",
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(counter.statusCode()).as(counter.body()).isEqualTo(201);
    assertThat(JSON.readTree(counter.body()).get("payment").get("method").asString())
        .isEqualTo("COUNTER");
    assertThat(JSON.readTree(counter.body()).get("payment").get("status").asString())
        .isEqualTo("PENDING");
    var risky =
        send(
            customer,
            "POST",
            orders,
            "{\"items\":[{\"editionId\":\""
                + edition
                + "\",\"quantity\":1}],\"paymentToken\":\""
                + paymentToken(customer, "4000000000000341")
                + "\"}",
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(risky.statusCode()).as(risky.body()).isEqualTo(201);
    String riskyPath = orders + "/" + JSON.readTree(risky.body()).get("id").asString();
    assertThat(send(manager, "POST", riskyPath + "/fulfill", null).statusCode()).isEqualTo(402);
    assertThat(JSON.readTree(get(manager, riskyPath).body()).get("status").asString())
        .isEqualTo("PLACED");
    assertThat(stock("stock_on_hand", branch, edition)).isEqualTo(3);
    assertThat(stock("stock_held", branch, edition)).isEqualTo(2);
    jdbc.update("UPDATE branches SET fulfills_delivery=false WHERE id=?", branch);
  }

  @Test
  void visitsAreBookedCheckedInBilledByTheHourAndReleaseTheirTableAndCopies() throws Exception {
    var branch = UUID.randomUUID();
    var table = UUID.randomUUID();
    var game = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO branches(id,name,address) VALUES (?, 'Visit test', 'Address')", branch);
    for (int day = 1; day <= 7; day++)
      jdbc.update(
          "INSERT INTO branch_opening_hours(branch_id,day_of_week,opens_at,closes_at) VALUES (?,?,'00:00','24:00')",
          branch,
          day);
    jdbc.update(
        "INSERT INTO branch_tables(id,branch_id,label,seats,hourly_rate_minor) VALUES (?,?,'T1',4,5000)",
        table,
        branch);
    jdbc.update(
        "INSERT INTO games(id,name,min_players,max_players,play_minutes_min,play_minutes_max) VALUES (?, 'Visit game',1,4,10,20)",
        game);
    jdbc.update(
        "INSERT INTO game_descriptions(game_id,locale,description) VALUES (?, 'en-US', 'Game')",
        game);
    jdbc.update(
        "INSERT INTO branch_games(branch_id,game_id,rental_enabled,rental_hourly_rate_minor) VALUES (?,?,true,4000)",
        branch,
        game);
    for (String code : List.of("A", "B"))
      jdbc.update(
          "INSERT INTO rental_copies(branch_id,game_id,asset_code) VALUES (?,?,?)",
          branch,
          game,
          code);

    String base = "/api/v1/branches/" + branch;
    var tomorrow = java.time.Instant.now().plus(java.time.Duration.ofDays(1));
    String from = tomorrow.toString();
    String until = tomorrow.plus(java.time.Duration.ofHours(2)).toString();
    String window = "startsAt=" + from + "&endsAt=" + until;
    var anonymous = browser();
    var free = get(anonymous, base + "/availability?" + window + "&partySize=4");
    assertThat(free.statusCode()).as(free.body()).isEqualTo(200);
    var freeView = JSON.readTree(free.body());
    assertThat(freeView.get("withinOpeningHours").asBoolean()).isTrue();
    assertThat(freeView.get("tables").get(0).get("available").asBoolean()).isTrue();
    assertThat(freeView.get("games").get(0).get("availableQuantity").asInt()).isEqualTo(2);
    assertThat(freeView.get("games").get(0).get("hourlyRate").get("amountMinor").asLong())
        .isEqualTo(4000);
    var tooBig =
        JSON.readTree(get(anonymous, base + "/availability?" + window + "&partySize=9").body());
    assertThat(tooBig.get("tables").get(0).get("unavailableReasons").toString())
        .contains("CAPACITY_EXCEEDED");
    assertThat(
            get(
                    anonymous,
                    base + "/availability?" + window + "&partySize=2&gameId=" + UUID.randomUUID())
                .statusCode())
        .isEqualTo(404);
    assertThat(
            get(
                    anonymous,
                    base + "/availability?startsAt=" + until + "&endsAt=" + from + "&partySize=2")
                .statusCode())
        .isEqualTo(400);

    // A customer books both copies for tomorrow; a retry returns the same booking.
    var customer = login("customer");
    assertThat(
            send(
                    customer,
                    "POST",
                    "/api/v1/me",
                    "{\"firstName\":\"Visit\",\"lastName\":\"Customer\",\"phoneNumber\":\"+66811112222\","
                        + "\"preferences\":{\"preferredLocale\":\"en-US\"}}")
                .statusCode())
        .isIn(200, 201);
    String plan =
        "\"tableId\":\""
            + table
            + "\",\"partySize\":3,\"games\":[{\"gameId\":\""
            + game
            + "\",\"quantity\":2}]";
    String booking = "{" + plan + ",\"startsAt\":\"" + from + "\",\"endsAt\":\"" + until + "\"}";
    String bookingKey = UUID.randomUUID().toString();
    var booked =
        send(customer, "POST", base + "/reservations", booking, "Idempotency-Key", bookingKey);
    assertThat(booked.statusCode()).as(booked.body()).isEqualTo(201);
    var reservation = JSON.readTree(booked.body());
    String reservationId = reservation.get("id").asString();
    assertThat(reservation.get("status").asString()).isEqualTo("CONFIRMED");
    assertThat(reservation.get("source").asString()).isEqualTo("CUSTOMER");
    assertThat(reservation.get("customer").get("kind").asString()).isEqualTo("REGISTERED");
    assertThat(reservation.get("tableHourlyRate").get("amountMinor").asLong()).isEqualTo(5000);
    assertThat(reservation.get("games").get(0).get("unitHourlyRate").get("amountMinor").asLong())
        .isEqualTo(4000);
    var replayed =
        send(customer, "POST", base + "/reservations", booking, "Idempotency-Key", bookingKey);
    assertThat(JSON.readTree(replayed.body()).get("id").asString()).isEqualTo(reservationId);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM reservation_copy_holds WHERE reservation_id=?::uuid",
                Integer.class,
                reservationId))
        .isEqualTo(2);

    // The table and both copies are now taken for that window, and nothing leaks about who.
    var taken = get(anonymous, base + "/availability?" + window + "&partySize=2");
    assertThat(taken.body()).doesNotContain("Visit", "+66811112222");
    var takenView = JSON.readTree(taken.body());
    assertThat(takenView.get("tables").get(0).get("unavailableReasons").toString())
        .contains("RESERVED");
    assertThat(takenView.get("games").get(0).get("availableQuantity").asInt()).isZero();
    var manager = login("manager");
    String guestBooking =
        "{\"source\":\"STAFF_BOOKING\",\"customer\":{\"kind\":\"GUEST\",\"displayName\":\"Other party\"},"
            + "\"tableId\":\""
            + table
            + "\",\"partySize\":2,\"games\":[],\"startsAt\":\""
            + tomorrow.plus(java.time.Duration.ofHours(1))
            + "\",\"endsAt\":\""
            + tomorrow.plus(java.time.Duration.ofHours(3))
            + "\"}";
    var clash =
        send(
            manager,
            "POST",
            base + "/staff/reservations",
            guestBooking,
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(clash.statusCode()).as(clash.body()).isEqualTo(409);

    // Rescheduling replaces the plan; a plan that cannot fit leaves the booking untouched.
    String reservationPath = base + "/reservations/" + reservationId;
    assertThat(
            send(
                    customer,
                    "PATCH",
                    reservationPath,
                    "{\"tableId\":\""
                        + table
                        + "\",\"partySize\":3,\"games\":[{\"gameId\":\""
                        + game
                        + "\",\"quantity\":3}],\"startsAt\":\""
                        + from
                        + "\",\"endsAt\":\""
                        + until
                        + "\"}")
                .statusCode())
        .isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM reservation_copy_holds WHERE reservation_id=?::uuid",
                Integer.class,
                reservationId))
        .isEqualTo(2);
    String later = tomorrow.plus(java.time.Duration.ofHours(4)).toString();
    var moved =
        send(
            customer,
            "PATCH",
            reservationPath,
            "{\"tableId\":\""
                + table
                + "\",\"partySize\":2,\"games\":[{\"gameId\":\""
                + game
                + "\",\"quantity\":1}],\"startsAt\":\""
                + later
                + "\",\"endsAt\":\""
                + tomorrow.plus(java.time.Duration.ofHours(5))
                + "\"}");
    assertThat(moved.statusCode()).as(moved.body()).isEqualTo(200);
    assertThat(JSON.readTree(moved.body()).get("partySize").asInt()).isEqualTo(2);
    assertThat(JSON.readTree(get(customer, "/api/v1/me/reservations").body()).get("items"))
        .hasSize(1);
    assertThat(get(login("staff"), reservationPath).statusCode()).isEqualTo(403);
    assertThat(send(customer, "POST", reservationPath + "/check-in", null).statusCode())
        .isEqualTo(403);
    // Staff cannot call a booking missed before it starts; the customer can still cancel it.
    assertThat(
            send(manager, "POST", reservationPath + "/no-show", "{\"reason\":\"Too early\"}")
                .statusCode())
        .isEqualTo(409);
    for (int attempt = 0; attempt < 2; attempt++) {
      var cancelled =
          send(customer, "POST", reservationPath + "/cancel", "{\"reason\":\"Plans changed\"}");
      assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
      assertThat(JSON.readTree(cancelled.body()).get("status").asString()).isEqualTo("CANCELLED");
    }
    assertThat(
            JSON.readTree(get(anonymous, base + "/availability?" + window + "&partySize=2").body())
                .get("games")
                .get(0)
                .get("availableQuantity")
                .asInt())
        .isEqualTo(2);

    // A walk-in starts now but is not checked in until staff say so.
    String walkIn =
        "{\"source\":\"WALK_IN\",\"customer\":{\"kind\":\"GUEST\",\"displayName\":\"Walk-in party\"},"
            + "\"tableId\":\""
            + table
            + "\",\"partySize\":2,\"games\":[{\"gameId\":\""
            + game
            + "\",\"quantity\":1}],\"endsAt\":\""
            + java.time.Instant.now().plus(java.time.Duration.ofHours(2))
            + "\"}";
    var seated =
        send(
            manager,
            "POST",
            base + "/staff/reservations",
            walkIn,
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(seated.statusCode()).as(seated.body()).isEqualTo(201);
    String visitId = JSON.readTree(seated.body()).get("id").asString();
    assertThat(JSON.readTree(seated.body()).get("status").asString()).isEqualTo("CONFIRMED");
    String visitPath = base + "/reservations/" + visitId;
    assertThat(
            JSON.readTree(get(anonymous, base + "/tables/" + table).body()).get("state").asString())
        .isEqualTo("RESERVED");
    assertThat(get(customer, visitPath).statusCode()).isEqualTo(404);

    String rentalId = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      var arrived = send(manager, "POST", visitPath + "/check-in", null);
      assertThat(arrived.statusCode()).as(arrived.body()).isEqualTo(200);
      var result = JSON.readTree(arrived.body());
      assertThat(result.get("reservation").get("status").asString()).isEqualTo("CHECKED_IN");
      assertThat(result.get("rental").get("status").asString()).isEqualTo("ACTIVE");
      assertThat(result.get("rental").get("charges")).hasSize(2);
      assertThat(result.get("rental").get("total")).isNull();
      if (rentalId != null)
        assertThat(result.get("rental").get("id").asString()).isEqualTo(rentalId);
      rentalId = result.get("rental").get("id").asString();
      assertThat(result.get("reservation").get("rentalId").asString()).isEqualTo(rentalId);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM rentals WHERE branch_id=?", Integer.class, branch))
        .isEqualTo(1);
    assertThat(
            JSON.readTree(get(anonymous, base + "/tables/" + table).body()).get("state").asString())
        .isEqualTo("OCCUPIED");
    assertThat(
            JSON.readTree(get(anonymous, base + "/games/" + game).body())
                .get("rentalAvailableNow")
                .asInt())
        .isEqualTo(1);
    assertThat(send(manager, "POST", visitPath + "/no-show", "{\"reason\":\"Here\"}").statusCode())
        .isEqualTo(409);
    // An active visit blocks a second walk-in now, but not a booking after its planned end.
    assertThat(
            send(
                    manager,
                    "POST",
                    base + "/staff/reservations",
                    walkIn,
                    "Idempotency-Key",
                    UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(409);
    var afterwards =
        JSON.readTree(get(anonymous, base + "/availability?" + window + "&partySize=2").body());
    assertThat(afterwards.get("tables").get(0).get("available").asBoolean()).isTrue();
    assertThat(afterwards.get("games").get(0).get("availableQuantity").asInt()).isEqualTo(2);

    // 61 minutes of play bill two started hours at the locked rates, once.
    jdbc.update(
        "UPDATE rentals SET started_at = now() - interval '61 minutes' WHERE id=?::uuid", rentalId);
    String rentalPath = base + "/rentals/" + rentalId;
    assertThat(send(login("staff"), "POST", rentalPath + "/check-out", null).statusCode())
        .isEqualTo(403);
    for (int attempt = 0; attempt < 2; attempt++) {
      var left = send(manager, "POST", rentalPath + "/check-out", null);
      assertThat(left.statusCode()).as(left.body()).isEqualTo(200);
      var result = JSON.readTree(left.body());
      assertThat(result.get("rental").get("status").asString()).isEqualTo("COMPLETED");
      assertThat(result.get("rental").get("billableHours").asInt()).isEqualTo(2);
      assertThat(result.get("rental").get("total").get("amountMinor").asLong()).isEqualTo(18000);
      assertThat(result.get("rental").get("charges").get(0).get("kind").asString())
          .isEqualTo("TABLE");
      assertThat(
              result
                  .get("rental")
                  .get("charges")
                  .get(0)
                  .get("lineTotal")
                  .get("amountMinor")
                  .asLong())
          .isEqualTo(10000);
      assertThat(result.get("reservation").get("status").asString()).isEqualTo("COMPLETED");
      assertThat(result.get("reservation").get("completedAt")).isNotNull();
      assertThat(result.get("reservation").get("checkedInAt")).isNotNull();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM rental_charges WHERE rental_id=?::uuid",
                Integer.class,
                rentalId))
        .isEqualTo(2);
    assertThat(
            JSON.readTree(get(anonymous, base + "/tables/" + table).body()).get("state").asString())
        .isEqualTo("AVAILABLE");
    assertThat(
            JSON.readTree(get(anonymous, base + "/games/" + game).body())
                .get("rentalAvailableNow")
                .asInt())
        .isEqualTo(2);
    assertThat(JSON.readTree(get(manager, base + "/rentals?status=COMPLETED").body()).get("items"))
        .hasSize(1);
    assertThat(
            JSON.readTree(get(manager, base + "/reservations?status=COMPLETED").body())
                .get("items"))
        .hasSize(1);
    assertThat(get(customer, rentalPath).statusCode()).isEqualTo(404);
  }

  static String walkIn(UUID table, int partySize, java.time.Duration length) {
    return "{\"source\":\"WALK_IN\",\"checkIn\":true,"
        + "\"customer\":{\"kind\":\"GUEST\",\"displayName\":\"QA Capacity Test\"},"
        + "\"tableId\":\""
        + table
        + "\",\"partySize\":"
        + partySize
        + ",\"games\":[],\"endsAt\":\""
        + java.time.Instant.now().plus(length)
        + "\"}";
  }

  int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  @Test
  void seatingAWalkInChecksInAtomicallyAndNeverDisturbsAnotherVisit() throws Exception {
    var branch = UUID.randomUUID();
    var closedBranch = UUID.randomUUID();
    var large = UUID.randomUUID();
    var small = UUID.randomUUID();
    var broken = UUID.randomUUID();
    var contested = UUID.randomUUID();
    var doubled = UUID.randomUUID();
    var closedTable = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO branches(id,name,address) VALUES (?, 'Walk-in test', 'Address')", branch);
    jdbc.update(
        "INSERT INTO branches(id,name,address) VALUES (?, 'Closed walk-in test', 'Address')",
        closedBranch);
    for (int day = 1; day <= 7; day++)
      jdbc.update(
          "INSERT INTO branch_opening_hours(branch_id,day_of_week,opens_at,closes_at) VALUES (?,?,'00:00','24:00')",
          branch,
          day);
    String insertTable =
        "INSERT INTO branch_tables(id,branch_id,label,seats,hourly_rate_minor,operational_status)"
            + " VALUES (?,?,?,?,5000,?)";
    jdbc.update(insertTable, large, branch, "T1", 4, "IN_SERVICE");
    jdbc.update(insertTable, small, branch, "T2", 2, "IN_SERVICE");
    jdbc.update(insertTable, broken, branch, "T3", 4, "OUT_OF_SERVICE");
    jdbc.update(insertTable, contested, branch, "T4", 4, "IN_SERVICE");
    jdbc.update(insertTable, doubled, branch, "T5", 4, "IN_SERVICE");
    jdbc.update(insertTable, closedTable, closedBranch, "T1", 4, "IN_SERVICE");

    String base = "/api/v1/branches/" + branch;
    String seat = base + "/staff/reservations";
    var manager = login("manager");
    var anonymous = browser();
    var twoHours = java.time.Duration.ofHours(2);
    String visitsOfBranch = "SELECT count(*) FROM reservations WHERE branch_id=?";
    String rentalsOfBranch = "SELECT count(*) FROM rentals WHERE branch_id=?";

    // A later booking on T1 limits how long a walk-in may stay there.
    var laterStart = java.time.Instant.now().plus(java.time.Duration.ofHours(1));
    var later =
        send(
            manager,
            "POST",
            seat,
            "{\"source\":\"STAFF_BOOKING\",\"customer\":{\"kind\":\"GUEST\",\"displayName\":\"Later party\"},"
                + "\"tableId\":\""
                + large
                + "\",\"partySize\":2,\"games\":[],\"startsAt\":\""
                + laterStart
                + "\",\"endsAt\":\""
                + laterStart.plus(twoHours)
                + "\"}",
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(later.statusCode()).as(later.body()).isEqualTo(201);

    // Every rejected seating leaves the branch exactly as it was.
    for (var rejected :
        List.of(
            java.util.Map.entry(walkIn(large, 2, twoHours), 409), // overlaps the later booking
            java.util.Map.entry(walkIn(small, 3, twoHours), 409), // party exceeds capacity
            java.util.Map.entry(walkIn(broken, 2, twoHours), 409), // out of service
            java.util.Map.entry(walkIn(UUID.randomUUID(), 2, twoHours), 404))) {
      var response =
          send(
              manager,
              "POST",
              seat,
              rejected.getKey(),
              "Idempotency-Key",
              UUID.randomUUID().toString());
      assertThat(response.statusCode()).as(response.body()).isEqualTo(rejected.getValue());
    }
    var outsideHours =
        send(
            manager,
            "POST",
            "/api/v1/branches/" + closedBranch + "/staff/reservations",
            walkIn(closedTable, 2, twoHours),
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(outsideHours.statusCode()).as(outsideHours.body()).isEqualTo(409);
    assertThat(count(visitsOfBranch, branch)).isEqualTo(1);
    assertThat(count(visitsOfBranch, closedBranch)).isZero();
    assertThat(count(rentalsOfBranch, branch)).isZero();

    // A failure while seating rolls back the walk-in too, and frees the key for a retry.
    String seatSmall = walkIn(small, 2, twoHours);
    String key = UUID.randomUUID().toString();
    jdbc.execute(
        "CREATE FUNCTION fail_walk_in_rental() RETURNS trigger AS $$"
            + " BEGIN RAISE EXCEPTION 'rental store unavailable'; END $$ LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER fail_walk_in_rental BEFORE INSERT ON rentals FOR EACH ROW"
            + " WHEN (NEW.branch_id = '"
            + branch
            + "'::uuid) EXECUTE FUNCTION fail_walk_in_rental()");
    try {
      var failed = send(manager, "POST", seat, seatSmall, "Idempotency-Key", key);
      assertThat(failed.statusCode()).as(failed.body()).isEqualTo(500);
    } finally {
      jdbc.execute("DROP TRIGGER fail_walk_in_rental ON rentals");
      jdbc.execute("DROP FUNCTION fail_walk_in_rental()");
    }
    assertThat(count(visitsOfBranch, branch)).isEqualTo(1);
    assertThat(count(rentalsOfBranch, branch)).isZero();
    assertThat(
            JSON.readTree(get(anonymous, base + "/tables/" + small).body()).get("state").asString())
        .isEqualTo("AVAILABLE");

    // A walk-in that fits seats the party at once; retries and double submits replay it.
    String visitId = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      var seated = send(manager, "POST", seat, seatSmall, "Idempotency-Key", key);
      assertThat(seated.statusCode()).as(seated.body()).isEqualTo(201);
      var visit = JSON.readTree(seated.body());
      assertThat(visit.get("status").asString()).isEqualTo("CHECKED_IN");
      assertThat(visit.get("source").asString()).isEqualTo("WALK_IN");
      assertThat(visit.get("tableId").asString()).isEqualTo(small.toString());
      assertThat(visit.get("rentalId").isNull()).isFalse();
      if (visitId != null) assertThat(visit.get("id").asString()).isEqualTo(visitId);
      visitId = visit.get("id").asString();
    }
    assertThat(count(rentalsOfBranch, branch)).isEqualTo(1);
    assertThat(
            send(manager, "POST", seat, walkIn(small, 1, twoHours), "Idempotency-Key", key)
                .statusCode())
        .isEqualTo(409);
    var fitsBeforeLater =
        send(
            manager,
            "POST",
            seat,
            walkIn(large, 2, java.time.Duration.ofMinutes(30)),
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(fitsBeforeLater.statusCode()).as(fitsBeforeLater.body()).isEqualTo(201);

    // An occupied table refuses another walk-in and keeps its guest.
    var occupied =
        send(
            manager,
            "POST",
            seat,
            walkIn(small, 2, twoHours),
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(occupied.statusCode()).as(occupied.body()).isEqualTo(409);
    var kept = JSON.readTree(get(manager, base + "/reservations/" + visitId).body());
    assertThat(kept.get("status").asString()).isEqualTo("CHECKED_IN");
    assertThat(kept.get("tableId").asString()).isEqualTo(small.toString());
    assertThat(kept.get("customer").get("displayName").asString()).isEqualTo("QA Capacity Test");
    assertThat(
            JSON.readTree(get(anonymous, base + "/tables/" + small).body()).get("state").asString())
        .isEqualTo("OCCUPIED");
    assertThat(
            count(
                "SELECT count(*) FROM reservations WHERE table_id=? AND status='CHECKED_IN'",
                small))
        .isEqualTo(1);

    // Simultaneous seatings wait for the table; one wins, and a shared key is one seating.
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      try (var connection = jdbc.getDataSource().getConnection()) {
        connection.setAutoCommit(false);
        try (var lock =
            connection.prepareStatement(
                "SELECT id FROM branch_tables WHERE id IN (?,?) FOR UPDATE")) {
          lock.setObject(1, contested);
          lock.setObject(2, doubled);
          lock.executeQuery();
        }
        String sharedKey = UUID.randomUUID().toString();
        String doubleClick = walkIn(doubled, 2, twoHours);
        var pending =
            List.of(
                executor.submit(
                    () ->
                        send(
                            manager,
                            "POST",
                            seat,
                            walkIn(contested, 2, twoHours),
                            "Idempotency-Key",
                            UUID.randomUUID().toString())),
                executor.submit(
                    () ->
                        send(
                            manager,
                            "POST",
                            seat,
                            walkIn(contested, 3, twoHours),
                            "Idempotency-Key",
                            UUID.randomUUID().toString())),
                executor.submit(
                    () -> send(manager, "POST", seat, doubleClick, "Idempotency-Key", sharedKey)),
                executor.submit(
                    () -> send(manager, "POST", seat, doubleClick, "Idempotency-Key", sharedKey)));
        assertThatThrownBy(() -> pending.get(0).get(1, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.TimeoutException.class);
        connection.commit();
        var statuses = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 2; i++)
          statuses.add(pending.get(i).get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        var first = pending.get(2).get(20, java.util.concurrent.TimeUnit.SECONDS);
        var second = pending.get(3).get(20, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(201);
        assertThat(JSON.readTree(second.body()).get("id").asString())
            .isEqualTo(JSON.readTree(first.body()).get("id").asString());
      }
    }
    for (var table : List.of(contested, doubled))
      assertThat(count("SELECT count(*) FROM reservations WHERE table_id=?", table)).isEqualTo(1);
    assertThat(count(rentalsOfBranch, branch)).isEqualTo(4);
  }

  @Test
  void foodPreOrdersHoldPantryStockAndAreChargedOnlyWhenThePartyArrives() throws Exception {
    var branch = UUID.randomUUID();
    var table = UUID.randomUUID();
    var cola = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO branches(id,name,address) VALUES (?, 'Pantry test', 'Address')", branch);
    for (int day = 1; day <= 7; day++)
      jdbc.update(
          "INSERT INTO branch_opening_hours(branch_id,day_of_week,opens_at,closes_at) VALUES (?,?,'00:00','24:00')",
          branch,
          day);
    jdbc.update(
        "INSERT INTO branch_tables(id,branch_id,label,seats,hourly_rate_minor) VALUES (?,?,'P1',4,5000)",
        table,
        branch);
    jdbc.update(
        "INSERT INTO menu_items(id,sku,category,names,price_minor) VALUES (?,?,'BEVERAGES',"
            + "'{\"en-US\":\"Cola\",\"th-TH\":\"โคล่า\"}'::jsonb,3500)",
        cola,
        "test-cola-" + cola);
    String base = "/api/v1/branches/" + branch;
    var anonymous = browser();

    // The menu is public; a branch that never stocked an item has none to offer.
    var menu = get(anonymous, base + "/menu");
    assertThat(menu.statusCode()).as(menu.body()).isEqualTo(200);
    assertThat(available(menu.body(), cola)).isZero();
    assertThat(get(anonymous, "/api/v1/branches/" + UUID.randomUUID() + "/menu").statusCode())
        .isEqualTo(404);
    var manager = login("manager");
    String receipt = "{\"delta\":3,\"reason\":\"Delivery\"}";
    assertThat(
            send(login("customer"), "POST", base + "/menu/" + cola + "/stock-adjustments", receipt)
                .statusCode())
        .isEqualTo(403);
    var received = send(manager, "POST", base + "/menu/" + cola + "/stock-adjustments", receipt);
    assertThat(received.statusCode()).as(received.body()).isEqualTo(201);
    assertThat(JSON.readTree(received.body()).get("onHand").asInt()).isEqualTo(3);
    assertThat(available(get(anonymous, base + "/menu").body(), cola)).isEqualTo(3);

    var customer = login("customer");
    assertThat(
            send(
                    customer,
                    "POST",
                    "/api/v1/me",
                    "{\"firstName\":\"Food\",\"lastName\":\"Customer\",\"phoneNumber\":\"+66811113333\","
                        + "\"preferences\":{\"preferredLocale\":\"en-US\"}}")
                .statusCode())
        .isIn(200, 201);
    var tomorrow = java.time.Instant.now().plus(java.time.Duration.ofDays(1));
    java.util.function.BiFunction<java.time.Instant, String, String> booking =
        (startsAt, token) ->
            "{\"tableId\":\""
                + table
                + "\",\"partySize\":2,\"games\":[],\"startsAt\":\""
                + startsAt
                + "\",\"endsAt\":\""
                + startsAt.plus(java.time.Duration.ofHours(2))
                + "\",\"preOrder\":{\"items\":[{\"menuItemId\":\""
                + cola
                + "\",\"quantity\":2}],\"paymentToken\":\""
                + token
                + "\"}}";

    // A visit beyond the card authorization window cannot carry food.
    var tooFar =
        send(
            customer,
            "POST",
            base + "/reservations",
            booking.apply(
                tomorrow.plus(java.time.Duration.ofDays(7)),
                paymentToken(customer, "4242 4242 4242 4242")),
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(tooFar.statusCode()).as(tooFar.body()).isEqualTo(409);

    // A declined card books nothing and holds nothing.
    var declined =
        send(
            customer,
            "POST",
            base + "/reservations",
            booking.apply(tomorrow, paymentToken(customer, "4000 0000 0000 0002")),
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(declined.statusCode()).as(declined.body()).isEqualTo(402);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM reservations WHERE branch_id=?", Integer.class, branch))
        .isZero();
    assertThat(available(get(anonymous, base + "/menu").body(), cola)).isEqualTo(3);

    // Asking for more than the branch has refuses the booking before the card is touched.
    String greedy =
        booking
            .apply(tomorrow, paymentToken(customer, "4242 4242 4242 4242"))
            .replace("\"quantity\":2", "\"quantity\":4");
    assertThat(
            send(
                    customer,
                    "POST",
                    base + "/reservations",
                    greedy,
                    "Idempotency-Key",
                    UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(409);

    // Two colas at 35 THB, less 10%, authorized on the card and held at the branch.
    var booked =
        send(
            customer,
            "POST",
            base + "/reservations",
            booking.apply(tomorrow, paymentToken(customer, "4242 4242 4242 4242")),
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(booked.statusCode()).as(booked.body()).isEqualTo(201);
    var preOrder = JSON.readTree(booked.body()).get("preOrder");
    assertThat(preOrder.get("subtotal").get("amountMinor").asLong()).isEqualTo(7000);
    assertThat(preOrder.get("discount").get("amountMinor").asLong()).isEqualTo(700);
    assertThat(preOrder.get("total").get("amountMinor").asLong()).isEqualTo(6300);
    assertThat(preOrder.get("items").get(0).get("name").asString()).isEqualTo("Cola");
    assertThat(preOrder.get("payment").get("status").asString()).isEqualTo("AUTHORIZED");
    assertThat(preOrder.get("payment").get("amount").get("amountMinor").asLong()).isEqualTo(6300);
    assertThat(available(get(anonymous, base + "/menu").body(), cola)).isEqualTo(1);
    String reservationPath =
        base + "/reservations/" + JSON.readTree(booked.body()).get("id").asString();
    assertThat(
            JSON.readTree(get(customer, "/api/v1/me/reservations").body())
                .get("items")
                .findValues("preOrder"))
        .isNotEmpty();
    // Held units cannot be corrected away.
    assertThat(
            send(
                    manager,
                    "POST",
                    base + "/menu/" + cola + "/stock-adjustments",
                    "{\"delta\":-2,\"reason\":\"Breakage\"}")
                .statusCode())
        .isEqualTo(409);
    // Nor can the visit be moved past the authorization window.
    String moveFar =
        "{\"tableId\":\""
            + table
            + "\",\"partySize\":2,\"games\":[],\"startsAt\":\""
            + tomorrow.plus(java.time.Duration.ofDays(7))
            + "\",\"endsAt\":\""
            + tomorrow.plus(java.time.Duration.ofDays(7)).plus(java.time.Duration.ofHours(1))
            + "\"}";
    assertThat(send(customer, "PATCH", reservationPath, moveFar).statusCode()).isEqualTo(409);

    // Moving a visit cannot renew an authorization obtained four days earlier.
    jdbc.update(
        "UPDATE reservation_preorders SET created_at = now() - interval '4 days' WHERE reservation_id=?::uuid",
        JSON.readTree(booked.body()).get("id").asString());
    String moveAfterOriginalWindow =
        "{\"tableId\":\""
            + table
            + "\",\"partySize\":2,\"games\":[],\"startsAt\":\""
            + tomorrow.plus(java.time.Duration.ofDays(2))
            + "\",\"endsAt\":\""
            + tomorrow.plus(java.time.Duration.ofDays(2)).plus(java.time.Duration.ofHours(1))
            + "\"}";
    var expiredMove = send(customer, "PATCH", reservationPath, moveAfterOriginalWindow);
    assertThat(expiredMove.statusCode()).as(expiredMove.body()).isEqualTo(409);
    assertThat(JSON.readTree(get(customer, reservationPath).body()).get("startsAt").asString())
        .isEqualTo(JSON.readTree(booked.body()).get("startsAt").asString());
    assertThat(available(get(anonymous, base + "/menu").body(), cola)).isEqualTo(1);

    // Cancelling returns the stock and voids the card authorization.
    var cancelled =
        send(customer, "POST", reservationPath + "/cancel", "{\"reason\":\"Plans changed\"}");
    assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
    assertThat(
            JSON.readTree(cancelled.body()).get("preOrder").get("payment").get("status").asString())
        .isEqualTo("VOIDED");
    assertThat(available(get(anonymous, base + "/menu").body(), cola)).isEqualTo(3);

    // A booking waits for a concurrent branch change, then rechecks its active state.
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      try (var connection = jdbc.getDataSource().getConnection()) {
        connection.setAutoCommit(false);
        try (var deactivate =
            connection.prepareStatement("UPDATE branches SET active=false WHERE id=?")) {
          deactivate.setObject(1, branch);
          deactivate.executeUpdate();
        }
        String pendingBooking =
            booking.apply(tomorrow, paymentToken(customer, "4242 4242 4242 4242"));
        var started = new java.util.concurrent.CountDownLatch(1);
        var pending =
            executor.submit(
                () -> {
                  started.countDown();
                  return send(
                      customer,
                      "POST",
                      base + "/reservations",
                      pendingBooking,
                      "Idempotency-Key",
                      UUID.randomUUID().toString());
                });
        assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> pending.get(1, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.TimeoutException.class);
        connection.commit();
        var rejected = pending.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(404);
      }
    }
    jdbc.update("UPDATE branches SET active=true WHERE id=?", branch);
    assertThat(available(get(anonymous, base + "/menu").body(), cola)).isEqualTo(3);

    // Check-in serves the food from held stock and captures the money, once.
    var soon = java.time.Instant.now().plus(java.time.Duration.ofMinutes(5));
    var visit =
        send(
            customer,
            "POST",
            base + "/reservations",
            booking.apply(soon, paymentToken(customer, "4242 4242 4242 4242")),
            "Idempotency-Key",
            UUID.randomUUID().toString());
    assertThat(visit.statusCode()).as(visit.body()).isEqualTo(201);
    String visitId = JSON.readTree(visit.body()).get("id").asString();
    jdbc.update(
        "UPDATE reservations SET starts_at = now() - interval '1 minute' WHERE id=?::uuid",
        visitId);
    for (int attempt = 0; attempt < 2; attempt++) {
      var arrived = send(manager, "POST", base + "/reservations/" + visitId + "/check-in", null);
      assertThat(arrived.statusCode()).as(arrived.body()).isEqualTo(200);
      assertThat(
              JSON.readTree(arrived.body())
                  .get("reservation")
                  .get("preOrder")
                  .get("payment")
                  .get("status")
                  .asString())
          .isEqualTo("CAPTURED");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT on_hand * 10 + held FROM branch_menu_stock WHERE branch_id=? AND menu_item_id=?",
                Integer.class,
                branch,
                cola))
        .isEqualTo(10);
  }

  static int available(String menuBody, UUID menuItemId) throws Exception {
    for (var item : JSON.readTree(menuBody).get("items"))
      if (item.get("item").get("id").asString().equals(menuItemId.toString()))
        return item.get("available").asInt();
    throw new AssertionError("Menu item " + menuItemId + " is not on the menu");
  }

  @Test
  void publicGameDetailHidesInactiveAndMissingTitles() throws Exception {
    var active = UUID.randomUUID();
    var inactive = UUID.randomUUID();
    for (var id : List.of(active, inactive)) {
      jdbc.update(
          "INSERT INTO games(id,name,min_players,max_players,play_minutes_min,play_minutes_max,active) VALUES (?, 'Catalog test',1,4,10,20,?)",
          id,
          id.equals(active));
      jdbc.update(
          "INSERT INTO game_descriptions(game_id,locale,description) VALUES (?, 'en-US', 'English text')",
          id);
    }
    var category =
        jdbc.queryForObject(
            "INSERT INTO game_categories(name) VALUES (?) RETURNING id",
            Long.class,
            "Category " + active);
    jdbc.update("INSERT INTO game_category_links VALUES (?, ?)", active, category);
    var response = get(browser(), "/api/v1/games/" + active);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(response.body()).get("id").asText()).isEqualTo(active.toString());
    assertThat(JSON.readTree(response.body()).get("descriptions").get("en-US").asText())
        .isEqualTo("English text");
    assertThat(get(browser(), "/api/v1/games/" + inactive).statusCode()).isEqualTo(404);
    assertThat(get(browser(), "/api/v1/games/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
  }

  @Test
  void migratesFreshDatabaseBeforeValidatingJpaEntities() {
    assertThat(
            jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class))
        .contains("accounts", "account_identities", "customers", "flyway_schema_history");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '001' and success",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void publicPagesNeedNoLogin() throws Exception {
    var browser = browser();
    assertThat(get(browser, "/api/v1/hello").statusCode()).isEqualTo(200);
    var session = get(browser, "/api/auth/session");
    assertThat(session.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(session.body()).get("authenticated").asBoolean()).isFalse();
    assertThat(session.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
    var protectedPage = get(browser, "/api/v1/me");
    assertThat(protectedPage.statusCode()).isEqualTo(401);
    assertThat(protectedPage.headers().firstValue("location")).isEmpty();
    var bearer =
        browser.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                .header("Authorization", "Bearer arbitrary-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(bearer.statusCode()).isEqualTo(401);
  }

  @Test
  void logsInThroughRealKeycloakAndRestoresIdentityFromValkey() throws Exception {
    var browser = login("staff");
    var profile = get(browser, "/api/auth/session");
    assertThat(profile.statusCode()).isEqualTo(200);
    var body = JSON.readTree(profile.body()).get("profile");
    assertThat(body.get("subject").asString()).isNotBlank();
    assertThat(body.get("username").asString()).isEqualTo("integration-staff");
    assertThat(profile.body())
        .contains("staff")
        .doesNotContain("access_token", "refresh_token", "id_token");
    assertThat(get(browser, "/api/v1/secure/whoami").body()).contains("integration-staff");
    assertThat(get(browser, "/api/auth/session").body()).contains("integration-staff");
    assertThat(redis.keys("theparlor:sessions:*")).isNotEmpty();
  }

  @Test
  void deletingAValkeySessionRevokesTheNextApiRequest() throws Exception {
    var browser = login("staff");
    var cookies =
        ((CookieManager) browser.cookieHandler().orElseThrow()).getCookieStore().getCookies();
    var encoded =
        cookies.stream()
            .filter(cookie -> cookie.getName().equals("THEPARLOR_SESSION"))
            .findFirst()
            .orElseThrow()
            .getValue();
    String sessionId =
        new String(java.util.Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    var keys = redis.keys("theparlor:sessions:*" + sessionId + "*");
    assertThat(keys).isNotEmpty();
    redis.delete(keys);
    assertThat(get(browser, "/api/v1/me").statusCode()).isEqualTo(401);
    assertThat(
            JSON.readTree(get(browser, "/api/auth/session").body())
                .get("authenticated")
                .asBoolean())
        .isFalse();
  }

  @Test
  void rejectsStaffAndManagerAtAdministratorEndpoints() throws Exception {
    assertThat(get(browser(), "/api/v1/admin/test-access").statusCode()).isEqualTo(401);
    assertThat(get(login("staff"), "/api/v1/admin/test-access").statusCode()).isEqualTo(403);
    assertThat(get(login("manager"), "/api/v1/admin/test-access").statusCode()).isEqualTo(403);
    assertThat(get(login("admin"), "/api/v1/admin/test-access").statusCode()).isEqualTo(200);
  }

  @Test
  void requiresCsrfForLogoutAndInvalidatesTheServerSession() throws Exception {
    var browser = login("staff");
    URI logout = URI.create("http://localhost:" + port + "/api/auth/logout");
    var denied =
        browser.send(
            HttpRequest.newBuilder(logout).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(denied.statusCode()).isEqualTo(403);
    assertThat(get(browser, "/api/v1/secure/whoami").statusCode()).isEqualTo(200);
    var csrf = JSON.readTree(get(browser, "/api/auth/csrf").body());
    var accepted =
        browser.send(
            HttpRequest.newBuilder(logout)
                .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(accepted.statusCode()).isEqualTo(302);
    assertThat(accepted.headers().firstValue("location").orElseThrow())
        .contains("/protocol/openid-connect/logout");
    assertThat(get(browser, "/api/v1/me").statusCode()).isEqualTo(401);
    var stale =
        browser.send(
            HttpRequest.newBuilder(logout)
                .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(stale.statusCode()).isIn(401, 403);
  }

  @Test
  void rejectsInvalidCallbackStateAndExposesProbes() throws Exception {
    var browser = browser();
    var invalid = get(browser, "/api/auth/callback/keycloak?code=invalid&state=invalid");
    assertThat(invalid.statusCode()).isEqualTo(302);
    assertThat(invalid.headers().firstValue("location").orElseThrow()).endsWith("/?login_error=1");
    assertThat(get(browser, "/api/v1/me").statusCode()).isEqualTo(401);
    assertThat(get(browser, "/actuator/health/liveness").statusCode()).isEqualTo(200);
    assertThat(get(browser, "/actuator/prometheus").body()).contains("jvm_memory_used_bytes");
  }
}
