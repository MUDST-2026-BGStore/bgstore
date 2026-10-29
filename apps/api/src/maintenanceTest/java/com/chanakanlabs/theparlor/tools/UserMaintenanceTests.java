package com.chanakanlabs.theparlor.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class UserMaintenanceTests {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3");

  @Container
  static GenericContainer<?> valkey =
      new GenericContainer<>("valkey/valkey:9.1.2").withExposedPorts(6379);

  @Container
  static KeycloakContainer keycloak =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.8.1")
          .withEnv("THEPARLOR_OIDC_CLIENT_SECRET", "maintenance-test-secret")
          .withRealmImportFile("realm-theparlor.json");

  Connection db;
  LettuceConnectionFactory factory;
  RedisTemplate<String, Object> redis;
  UserMaintenance.Keycloak admin;
  String issuer;

  @BeforeEach
  void setup() throws Exception {
    db =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (var statement = db.createStatement()) {
      statement.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    }
    org.flywaydb.core.Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("filesystem:src/main/resources/db/migration")
        .load()
        .migrate();
    factory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(valkey.getHost(), valkey.getMappedPort(6379)));
    factory.afterPropertiesSet();
    redis = UserMaintenance.redis(factory);
    try (var connection = factory.getConnection()) {
      connection.serverCommands().flushDb();
    }
    String base = keycloak.getAuthServerUrl().replaceAll("/+$", "");
    issuer = base + "/realms/theparlor";
    admin =
        new UserMaintenance.Keycloak(
            base, keycloak.getAdminUsername(), keycloak.getAdminPassword());
    for (var user : admin.users())
      admin.request("DELETE", "/users/" + user.path("id").asText(), null);
  }

  @AfterEach
  void cleanup() throws Exception {
    if (db != null) db.close();
    if (factory != null) factory.destroy();
  }

  String user(String username) {
    var representation = new UserRepresentation();
    representation.setUsername(username);
    representation.setEnabled(true);
    var users = keycloak.getKeycloakAdminClient().realm("theparlor").users();
    try (var response = users.create(representation)) {
      assertThat(response.getStatus()).isEqualTo(201);
    }
    return users.searchByUsername(username, true).getFirst().getId();
  }

  String account(String subject) throws Exception {
    String account =
        UserMaintenance.query(
                db,
                "INSERT INTO accounts(first_name,last_name,phone_number,preferences) VALUES ('Test','Person','123','{\"preferredLocale\":\"en-US\"}') RETURNING id::text",
                List.of())
            .getFirst();
    link(account, issuer, subject);
    try (var statement =
        db.prepareStatement("INSERT INTO customers(account_id) VALUES (?::uuid)")) {
      statement.setString(1, account);
      statement.executeUpdate();
    }
    return account;
  }

  void link(String account, String identityIssuer, String subject) throws Exception {
    try (var statement =
        db.prepareStatement(
            "INSERT INTO account_identities(issuer,subject,account_id) VALUES (?,?,?::uuid)")) {
      statement.setString(1, identityIssuer);
      statement.setString(2, subject);
      statement.setString(3, account);
      statement.executeUpdate();
    }
  }

  String session(String subject, String identityIssuer) {
    var repository = new RedisSessionRepository(redis);
    repository.setRedisKeyNamespace("theparlor:sessions");
    return saveSession(repository, subject, identityIssuer);
  }

  <S extends Session> String saveSession(
      SessionRepository<S> repository, String subject, String identityIssuer) {
    S session = repository.createSession();
    if (subject != null) {
      var token =
          new OidcIdToken(
              "test-token",
              Instant.now(),
              Instant.now().plusSeconds(600),
              Map.of("iss", identityIssuer, "sub", subject));
      var principal = new DefaultOidcUser(List.of(), token);
      session.setAttribute(
          "SPRING_SECURITY_CONTEXT",
          new SecurityContextImpl(
              new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak")));
    }
    repository.save(session);
    return UserMaintenance.NAMESPACE + "sessions:" + session.getId();
  }

  List<String> rows(String table) throws Exception {
    return UserMaintenance.query(db, "SELECT id::text FROM " + table, List.of());
  }

  @Test
  void previewThenDeleteRemovesAllLinkedIdentitiesButPreservesOtherUsersAndWalkIns()
      throws Exception {
    String alice = user("alice");
    String alias = user("alice-alias");
    String bob = user("bob");
    String owned = account(alice);
    link(owned, issuer, alias);
    String other = account(bob);
    String walkIn =
        UserMaintenance.query(
                db, "INSERT INTO customers DEFAULT VALUES RETURNING id::text", List.of())
            .getFirst();
    String first = session(alice, issuer);
    String second = session(alias, issuer);
    String otherSession = session(bob, issuer);
    String differentIssuer = session(alice, "https://another.example/realm");
    String anonymous = session(null, issuer);
    var preview =
        UserMaintenance.run(
            new UserMaintenance.Options(false, "alice", "", true), db, redis, admin, issuer);
    assertThat(preview.accounts()).containsExactly(owned);
    assertThat(preview.identities()).isEqualTo(2);
    assertThat(preview.customers()).isEqualTo(1);
    assertThat(preview.sessions()).containsExactlyInAnyOrder(first, second);
    assertThat(rows("accounts")).hasSize(2);
    assertThat(redis.hasKey(first)).isTrue();
    assertThat(admin.request("GET", "/users/" + alice, null).path("enabled").asBoolean()).isTrue();

    UserMaintenance.run(
        new UserMaintenance.Options(false, "alice", "", false), db, redis, admin, issuer);
    assertThat(rows("accounts")).containsExactly(other);
    assertThat(rows("customers")).hasSize(2).contains(walkIn);
    assertThat(UserMaintenance.query(db, "SELECT subject FROM account_identities", List.of()))
        .containsExactly(bob);
    assertThat(redis.hasKey(first)).isFalse();
    assertThat(redis.hasKey(second)).isFalse();
    for (String key : List.of(otherSession, differentIssuer, anonymous))
      assertThat(redis.hasKey(key)).isTrue();
    assertThat(admin.request("GET", "/users/" + alice, null)).isNull();
    assertThat(admin.request("GET", "/users/" + alias, null)).isNull();
    assertThat(admin.request("GET", "/users/" + bob, null).path("enabled").asBoolean()).isTrue();
  }

  @Test
  void wipesUnprovisionedUsersOrphanAccountsAndAnonymousSessionsButKeepsWalkInsAndOtherRedisKeys()
      throws Exception {
    String alice = user("alice");
    user("unprovisioned");
    account(alice);
    String orphan = account(UUID.randomUUID().toString());
    try (var statement = db.createStatement()) {
      statement.execute("DELETE FROM account_identities WHERE account_id = '" + orphan + "'");
    }
    String walkIn =
        UserMaintenance.query(
                db, "INSERT INTO customers DEFAULT VALUES RETURNING id::text", List.of())
            .getFirst();
    String anonymous = session(null, issuer);
    redis.opsForHash().put("another-app:session", "value", "preserve");
    UserMaintenance.run(new UserMaintenance.Options(true, "", "", false), db, redis, admin, issuer);
    assertThat(admin.users()).isEmpty();
    assertThat(rows("accounts")).isEmpty();
    assertThat(rows("customers")).containsExactly(walkIn);
    assertThat(redis.hasKey(anonymous)).isFalse();
    assertThat(redis.hasKey("another-app:session")).isTrue();
    // Repeating the wipe must keep surviving data and report no new deletions.
    var repeated =
        UserMaintenance.run(
            new UserMaintenance.Options(true, "", "", false), db, redis, admin, issuer);
    assertThat(repeated.accounts()).isEmpty();
    assertThat(repeated.sessions()).isEmpty();
    assertThat(admin.users()).isEmpty();
    assertThat(rows("customers")).containsExactly(walkIn);
    assertThat(redis.hasKey("another-app:session")).isTrue();
  }

  @Test
  void userIdCleansDataAfterTheKeycloakUserWasAlreadyRemoved() throws Exception {
    String subject = UUID.randomUUID().toString();
    account(subject);
    String key = session(subject, issuer);
    UserMaintenance.run(
        new UserMaintenance.Options(false, "", subject, false), db, redis, admin, issuer);
    assertThat(rows("accounts")).isEmpty();
    assertThat(rows("customers")).isEmpty();
    assertThat(redis.hasKey(key)).isFalse();
  }

  @Test
  void externalIdentityAndMissingUsernameFailBeforeDeletingAnything() throws Exception {
    String alice = user("alice");
    String account = account(alice);
    link(account, "https://another.example/realm", "external");
    assertThatThrownBy(
            () ->
                UserMaintenance.run(
                    new UserMaintenance.Options(false, "alice", "", false),
                    db,
                    redis,
                    admin,
                    issuer))
        .hasMessageContaining("outside the local theparlor realm");
    assertThat(rows("accounts")).containsExactly(account);
    assertThat(admin.request("GET", "/users/" + alice, null).path("enabled").asBoolean()).isTrue();
    assertThatThrownBy(
            () ->
                UserMaintenance.run(
                    new UserMaintenance.Options(false, "missing", "", false),
                    db,
                    redis,
                    admin,
                    issuer))
        .hasMessageContaining("exactly one Keycloak user");
  }

  @Test
  void restrictiveForeignKeysRollBackDatabaseChangesAndAllowRetry() throws Exception {
    String alice = user("alice");
    String owned = account(alice);
    try (var statement = db.createStatement()) {
      statement.execute("CREATE TABLE future_resource(account_id UUID REFERENCES accounts(id))");
      statement.execute("INSERT INTO future_resource VALUES ('" + owned + "')");
    }
    assertThatThrownBy(
            () ->
                UserMaintenance.run(
                    new UserMaintenance.Options(false, "alice", "", false),
                    db,
                    redis,
                    admin,
                    issuer))
        .hasMessageContaining("foreign key constraint");
    assertThat(rows("accounts")).containsExactly(owned);
    assertThat(rows("customers")).hasSize(1);
    assertThat(admin.request("GET", "/users/" + alice, null).path("enabled").asBoolean()).isFalse();
    try (var statement = db.createStatement()) {
      statement.execute("DROP TABLE future_resource");
    }
    UserMaintenance.run(
        new UserMaintenance.Options(false, "alice", "", false), db, redis, admin, issuer);
    assertThat(rows("accounts")).isEmpty();
    assertThat(admin.request("GET", "/users/" + alice, null)).isNull();
  }

  @Test
  void wipesMoreThanOnePageOfUsersAndPreservesClientServiceAccounts() throws Exception {
    var realm = keycloak.getKeycloakAdminClient().realm("theparlor");
    var client = new ClientRepresentation();
    client.setClientId("maintenance-service");
    client.setEnabled(true);
    client.setServiceAccountsEnabled(true);
    client.setPublicClient(false);
    try (var response = realm.clients().create(client)) {
      assertThat(response.getStatus()).isEqualTo(201);
    }
    var resource =
        realm
            .clients()
            .get(realm.clients().findByClientId("maintenance-service").getFirst().getId());
    String serviceAccount = resource.getServiceAccountUser().getId();
    for (int i = 0; i < 101; i++) user("batch-" + i);
    admin.token = "expired-token";
    assertThat(admin.users()).hasSize(101);
    assertThatThrownBy(
            () ->
                UserMaintenance.run(
                    new UserMaintenance.Options(false, "", serviceAccount, false),
                    db,
                    redis,
                    admin,
                    issuer))
        .hasMessageContaining("service account");
    UserMaintenance.run(new UserMaintenance.Options(true, "", "", false), db, redis, admin, issuer);
    assertThat(admin.users()).isEmpty();
    assertThat(admin.request("GET", "/users/" + serviceAccount, null)).isNotNull();
  }
}
