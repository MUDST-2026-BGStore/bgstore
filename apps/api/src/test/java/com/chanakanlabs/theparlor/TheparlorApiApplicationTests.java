package com.chanakanlabs.theparlor;

import static org.assertj.core.api.Assertions.assertThat;

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
    for (String role : List.of("staff", "manager", "admin")) {
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
