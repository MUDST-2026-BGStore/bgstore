package com.chanakanlabs.theparlor.tools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Explicitly invoked local tooling; never loaded by the API. */
public final class UserMaintenance {
  static final String NAMESPACE = "theparlor:sessions:";
  static final ObjectMapper JSON = new ObjectMapper();

  record Options(boolean all, String username, String userId, boolean dryRun) {
    static Options from(Map<String, String> env) {
      String mode = env.getOrDefault("THEPARLOR_DELETE_MODE", "");
      if (!Set.of("delete-user", "wipe-users").contains(mode))
        throw new IllegalArgumentException("Expected delete-user or wipe-users.");
      String username = env.getOrDefault("THEPARLOR_DELETE_USERNAME", "");
      String userId = env.getOrDefault("THEPARLOR_DELETE_USER_ID", "");
      boolean all = mode.equals("wipe-users");
      int selectors = (username.isEmpty() ? 0 : 1) + (userId.isEmpty() ? 0 : 1);
      if (all ? selectors != 0 : selectors != 1)
        throw new IllegalArgumentException(
            "Select exactly one USERNAME or USER_ID for delete-user; neither for wipe-users.");
      boolean dryRun = "1".equals(env.get("THEPARLOR_DELETE_DRY_RUN"));
      if (!dryRun && !"1".equals(env.get("THEPARLOR_DELETE_CONFIRM")))
        throw new IllegalArgumentException("Use DRY_RUN=1 to preview or CONFIRM=1 to delete.");
      return new Options(all, username, userId, dryRun);
    }
  }

  record Plan(
      Set<String> subjects,
      List<String> accounts,
      int identities,
      int customers,
      List<String> sessions) {}

  public static void main(String[] args) throws Exception {
    Map<String, String> env = System.getenv();
    Options options = Options.from(env);
    // Deliberately ignore production datasource/Redis/issuer overrides: these commands target local
    // ports.
    String base = "http://localhost:" + port(env, "KEYCLOAK_PORT", "8081");
    String issuer = base + "/realms/theparlor";
    var keycloak =
        new Keycloak(
            base,
            env.getOrDefault("KC_BOOTSTRAP_ADMIN_USERNAME", "admin"),
            required(env, "KC_BOOTSTRAP_ADMIN_PASSWORD"));
    var redisConfig =
        new RedisStandaloneConfiguration("localhost", port(env, "VALKEY_PORT", "6379"));
    redisConfig.setPassword(required(env, "VALKEY_PASSWORD"));
    var factory = new LettuceConnectionFactory(redisConfig);
    factory.afterPropertiesSet();
    try (var connection =
        DriverManager.getConnection(
            "jdbc:postgresql://localhost:"
                + port(env, "POSTGRES_PORT", "5432")
                + "/"
                + encode(env.getOrDefault("POSTGRES_DB", "theparlor")),
            env.getOrDefault("POSTGRES_USER", "theparlor"),
            required(env, "POSTGRES_PASSWORD"))) {
      run(options, connection, redis(factory), keycloak, issuer);
    } finally {
      factory.destroy();
    }
  }

  static int port(Map<String, String> env, String name, String fallback) {
    int value = Integer.parseInt(env.getOrDefault(name, fallback));
    if (value < 1 || value > 65535) throw new IllegalArgumentException("Invalid " + name);
    return value;
  }

  static String required(Map<String, String> env, String name) {
    String value = env.get(name);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Set " + name + " in .env.");
    return value;
  }

  static RedisTemplate<String, Object> redis(LettuceConnectionFactory factory) {
    var redis = new RedisTemplate<String, Object>();
    redis.setConnectionFactory(factory);
    redis.setKeySerializer(new StringRedisSerializer());
    redis.setHashKeySerializer(new StringRedisSerializer());
    redis.setHashValueSerializer(new JdkSerializationRedisSerializer());
    redis.afterPropertiesSet();
    return redis;
  }

  static Plan run(
      Options options,
      Connection db,
      RedisTemplate<String, Object> redis,
      Keycloak keycloak,
      String issuer)
      throws Exception {
    db.setAutoCommit(false);
    try {
      if (!options.dryRun()) {
        // Serialize local profile writes while resolving and removing linked records.
        try (var statement = db.createStatement()) {
          statement.execute("SET LOCAL lock_timeout = '10s'");
          statement.execute(
              "LOCK TABLE accounts, account_identities, customers IN SHARE ROW EXCLUSIVE MODE");
        }
      }
      Set<String> serviceAccounts = keycloak.serviceAccountIds();
      Set<String> subjects = new LinkedHashSet<>();
      if (options.all()) {
        for (var user : keycloak.users()) {
          String subject = user.path("id").asText();
          if (!serviceAccounts.contains(subject)) subjects.add(subject);
        }
      } else if (!options.userId().isEmpty()) {
        subjects.add(options.userId());
      } else {
        var matches =
            keycloak.request(
                "GET", "/users?exact=true&username=" + encode(options.username()), null);
        if (matches.size() != 1)
          throw new IllegalArgumentException(
              "Expected exactly one Keycloak user for USERNAME; use USER_ID to clean up an already deleted identity.");
        subjects.add(matches.get(0).path("id").asText());
      }
      List<String> accounts =
          options.all()
              ? query(db, "SELECT id::text FROM accounts", List.of())
              : query(
                  db,
                  "SELECT DISTINCT account_id::text FROM account_identities WHERE issuer = ? AND subject = ANY (?::text[])",
                  List.of(issuer, subjects.toArray(String[]::new)));
      // An account can own multiple OIDC identities: all must be removed together.
      var linkedIssuers =
          query(
              db,
              "SELECT DISTINCT issuer FROM account_identities WHERE account_id = ANY (?::uuid[])",
              List.of((Object) accounts.toArray(String[]::new)));
      if (linkedIssuers.stream().anyMatch(value -> !value.equals(issuer)))
        throw new IllegalStateException(
            "An account has identities outside the local theparlor realm. Resolve those links before deleting it.");
      subjects.addAll(
          query(
              db,
              "SELECT subject FROM account_identities WHERE account_id = ANY (?::uuid[])",
              List.of((Object) accounts.toArray(String[]::new))));
      // Validate all identities before the first mutation, including pagination and linked users.
      List<JsonNode> users = new ArrayList<>();
      for (String subject : subjects) {
        var user = keycloak.request("GET", "/users/" + encode(subject), null);
        if (user == null) continue; // Allows USER_ID recovery after partial cleanup.
        if (serviceAccounts.contains(subject) || user.hasNonNull("serviceAccountClientId"))
          throw new IllegalStateException("Refusing to delete a Keycloak client service account.");
        users.add(user);
      }
      int identities =
          count(
              db,
              "SELECT count(*) FROM account_identities WHERE account_id = ANY (?::uuid[])",
              accounts);
      int customers =
          count(db, "SELECT count(*) FROM customers WHERE account_id = ANY (?::uuid[])", accounts);
      var sessions = sessionKeys(redis, options.all(), issuer, subjects);
      var plan =
          new Plan(Set.copyOf(subjects), List.copyOf(accounts), identities, customers, sessions);
      System.out.printf(
          "%s local theparlor: %d Keycloak users, %d accounts, %d identity links, %d linked customers, %d session keys.%n",
          options.dryRun() ? "Previewing" : "Deleting",
          users.size(),
          accounts.size(),
          identities,
          customers,
          sessions.size());
      if (!options.all()) System.out.println("Keycloak user IDs: " + String.join(", ", subjects));
      if (options.dryRun()) {
        db.rollback();
        System.out.println("Preview complete; nothing was changed.");
        return plan;
      }
      // Disable login and revoke SSO before removing browser sessions or persistent profiles.
      // If any later step fails, disabled identities remain discoverable for a safe retry.
      for (var user : users) {
        keycloak.request(
            "PUT", "/users/" + encode(user.path("id").asText()), Map.of("enabled", false));
        keycloak.request("POST", "/users/" + encode(user.path("id").asText()) + "/logout", null);
      }
      var currentSessions = sessionKeys(redis, options.all(), issuer, subjects);
      if (!currentSessions.isEmpty()) redis.delete(currentSessions);
      // customers.account_id uses SET NULL, so explicitly delete owned customer records first.
      execute(db, "DELETE FROM customers WHERE account_id = ANY (?::uuid[])", accounts);
      execute(db, "DELETE FROM accounts WHERE id = ANY (?::uuid[])", accounts);
      db.commit(); // account_identities cascade; other restrictive FKs fail rather than silently
      // orphaning data.
      for (var user : users)
        keycloak.request("DELETE", "/users/" + encode(user.path("id").asText()), null);
      System.out.println("User cleanup complete.");
      return plan;
    } catch (Exception failure) {
      db.rollback();
      System.err.println(
          "Cleanup did not complete. If deletion started, some Keycloak users may be disabled. Retry the same command (or USER_ID after a partial deletion).");
      throw failure;
    } finally {
      db.setAutoCommit(true);
    }
  }

  static List<String> sessionKeys(
      RedisTemplate<String, Object> redis, boolean all, String issuer, Set<String> subjects) {
    List<String> keys = new ArrayList<>();
    try (var cursor =
        redis.scan(ScanOptions.scanOptions().match(NAMESPACE + "*").count(200).build())) {
      while (cursor.hasNext()) {
        String key = cursor.next();
        if (all) keys.add(key);
        else if (key.startsWith(NAMESPACE + "sessions:")) {
          Object value = redis.opsForHash().get(key, "sessionAttr:SPRING_SECURITY_CONTEXT");
          if (value instanceof SecurityContext context
              && context.getAuthentication() != null
              && context.getAuthentication().getPrincipal() instanceof OidcUser principal
              && principal.getIssuer() != null
              && issuer.equals(principal.getIssuer().toString())
              && subjects.contains(principal.getSubject())) keys.add(key);
        }
      }
    }
    return keys;
  }

  static List<String> query(Connection db, String sql, List<Object> parameters) throws Exception {
    try (var statement = db.prepareStatement(sql)) {
      for (int index = 0; index < parameters.size(); index++) {
        Object value = parameters.get(index);
        if (value instanceof String[] array)
          statement.setArray(index + 1, db.createArrayOf("text", array));
        else statement.setObject(index + 1, value);
      }
      List<String> values = new ArrayList<>();
      try (var rows = statement.executeQuery()) {
        while (rows.next()) values.add(rows.getString(1));
      }
      return values;
    }
  }

  static int count(Connection db, String sql, List<String> accounts) throws Exception {
    return Integer.parseInt(
        query(db, sql, List.of((Object) accounts.toArray(String[]::new))).getFirst());
  }

  static void execute(Connection db, String sql, List<String> accounts) throws Exception {
    try (var statement = db.prepareStatement(sql)) {
      statement.setArray(1, db.createArrayOf("text", accounts.toArray(String[]::new)));
      statement.executeUpdate();
    }
  }

  static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  static final class Keycloak {
    final String base;
    final String username;
    final String password;
    String token;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    Keycloak(String base, String username, String password) throws Exception {
      this.base = base;
      this.username = username;
      this.password = password;
      login();
    }

    void login() throws Exception {
      var response =
          http.send(
              HttpRequest.newBuilder(
                      URI.create(base + "/realms/master/protocol/openid-connect/token"))
                  .timeout(Duration.ofSeconds(30))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "grant_type=password&client_id=admin-cli&username="
                              + encode(username)
                              + "&password="
                              + encode(password)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new IllegalStateException("Keycloak admin login failed: " + response.statusCode());
      token = JSON.readTree(response.body()).path("access_token").asText();
    }

    JsonNode request(String method, String path, Object body) throws Exception {
      return request(method, path, body, true);
    }

    JsonNode request(String method, String path, Object body, boolean retryAuthentication)
        throws Exception {
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/admin/realms/theparlor" + path))
                  .timeout(Duration.ofSeconds(30))
                  .header("Authorization", "Bearer " + token)
                  .header("Content-Type", "application/json")
                  .method(
                      method,
                      body == null
                          ? HttpRequest.BodyPublishers.noBody()
                          : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      // Large wipes may outlive Keycloak's short admin access-token lifetime.
      if (response.statusCode() == 401 && retryAuthentication) {
        login();
        return request(method, path, body, false);
      }
      if (response.statusCode() == 404 && (method.equals("GET") || method.equals("DELETE")))
        return null;
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("Keycloak " + method + " failed: " + response.statusCode());
      return response.body().isBlank() ? null : JSON.readTree(response.body());
    }

    List<JsonNode> users() throws Exception {
      List<JsonNode> users = new ArrayList<>();
      for (int first = 0; ; first += 100) {
        var page =
            request("GET", "/users?first=" + first + "&max=100&briefRepresentation=false", null);
        for (var user : page) if (!user.hasNonNull("serviceAccountClientId")) users.add(user);
        if (page.size() < 100) return users;
      }
    }

    Set<String> serviceAccountIds() throws Exception {
      Set<String> subjects = new LinkedHashSet<>();
      for (int first = 0; ; first += 100) {
        var clients = request("GET", "/clients?first=" + first + "&max=100", null);
        for (var client : clients) {
          if (!client.path("serviceAccountsEnabled").asBoolean(false)) continue;
          var user =
              request(
                  "GET",
                  "/clients/" + encode(client.path("id").asText()) + "/service-account-user",
                  null);
          if (user != null) subjects.add(user.path("id").asText());
        }
        if (clients.size() < 100) return subjects;
      }
    }
  }
}
