package com.chanakanlabs.theparlor.tools;

import java.io.InputStream;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resets all business tables and re-seeds the local dev database with the reference data (branches,
 * opening hours, catalog, editions, branch offerings, sale stock, tables and rental copies) from
 * seed/reference-data.json. Catalog reference data is authoritative for development; accounts,
 * customers and identity data are left alone. Explicitly invoked local tooling; never loaded by the
 * API. Run through `make seed-dev` (tools/seed-dev.mjs), which loads .env.
 */
public final class ReferenceDataSeed {
  static final ObjectMapper JSON = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    var env = System.getenv();
    try (var connection =
        DriverManager.getConnection(
            "jdbc:postgresql://localhost:"
                + port(env, "POSTGRES_PORT", "5432")
                + "/"
                + env.getOrDefault("POSTGRES_DB", "theparlor"),
            env.getOrDefault("POSTGRES_USER", "theparlor"),
            required(env, "POSTGRES_PASSWORD"))) {
      connection.setAutoCommit(false);
      run(connection);
      connection.commit();
    }
  }

  static void run(java.sql.Connection connection) throws Exception {
    JsonNode data;
    try (InputStream resource =
        ReferenceDataSeed.class.getResourceAsStream("/seed/reference-data.json")) {
      if (resource == null) throw new IllegalStateException("seed/reference-data.json missing");
      data = JSON.readTree(resource);
    }

    // One business-data reset; application accounts and identity links are retained.
    try (var reset = connection.createStatement()) {
      reset.executeUpdate(
          "TRUNCATE branches, games, game_categories, game_mechanics, idempotency_keys RESTART IDENTITY CASCADE");
    }

    var branchIds = new LinkedHashMap<String, UUID>();
    try (var insert =
        connection.prepareStatement(
            "INSERT INTO branches (name, address) VALUES (?, ?) RETURNING id")) {
      for (var branch : data.path("branches")) {
        insert.setString(1, branch.path("name").asString(""));
        insert.setString(2, branch.path("address").asString(""));
        branchIds.put(branch.path("key").asString(""), insertOne(insert));
      }
    }

    try (var insert =
        connection.prepareStatement(
            "INSERT INTO branch_opening_hours (branch_id, day_of_week, opens_at, closes_at) "
                + "VALUES (?, ?, ?, ?)")) {
      for (var branchId : branchIds.values()) {
        for (var period : data.path("openingHours")) {
          insert.setObject(1, branchId);
          insert.setShort(2, period.path("dayOfWeek").asShort());
          insert.setTime(
              3,
              java.sql.Time.valueOf(java.time.LocalTime.parse(period.path("opens").asString(""))));
          insert.setTime(
              4,
              java.sql.Time.valueOf(java.time.LocalTime.parse(period.path("closes").asString(""))));
          insert.addBatch();
        }
      }
      insert.executeBatch();
    }

    var categoryIds = insertNames(connection, "game_categories", data.path("categories"));
    var mechanicIds = insertNames(connection, "game_mechanics", data.path("mechanics"));

    Map<String, UUID> gameIds = new HashMap<>();
    Map<String, Map<String, UUID>> editionIds = new HashMap<>();
    try (var insertGame =
            connection.prepareStatement(
                "INSERT INTO games (name, designer, difficulty, min_players, max_players, "
                    + "play_minutes_min, play_minutes_max, teach_minutes, setup_minutes) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id");
        var insertDescription =
            connection.prepareStatement(
                "INSERT INTO game_descriptions (game_id, locale, description) VALUES (?, ?, ?)");
        var insertImage =
            connection.prepareStatement(
                "INSERT INTO game_images (game_id, url, sort_order) VALUES (?, ?, 0)");
        var insertCategoryLink =
            connection.prepareStatement(
                "INSERT INTO game_category_links (game_id, category_id) VALUES (?, ?)");
        var insertMechanicLink =
            connection.prepareStatement(
                "INSERT INTO game_mechanic_links (game_id, mechanic_id) VALUES (?, ?)");
        var insertEdition =
            connection.prepareStatement(
                "INSERT INTO game_editions (game_id, kind, language_code) "
                    + "VALUES (?, 'BASE_GAME', ?) RETURNING id")) {
      for (var game : data.path("games")) {
        var key = game.path("key").asString("");
        insertGame.setString(1, game.path("name").asString(""));
        insertGame.setString(2, game.path("designer").asString(null));
        insertGame.setString(3, game.path("difficulty").asString(null));
        insertGame.setInt(4, game.path("players").get(0).asInt());
        insertGame.setInt(5, game.path("players").get(1).asInt());
        insertGame.setInt(6, game.path("playMinutes").get(0).asInt());
        insertGame.setInt(7, game.path("playMinutes").get(1).asInt());
        insertGame.setObject(
            8,
            game.hasNonNull("teachMinutes") ? game.path("teachMinutes").asInt() : null,
            java.sql.Types.INTEGER);
        insertGame.setObject(
            9,
            game.hasNonNull("setupMinutes") ? game.path("setupMinutes").asInt() : null,
            java.sql.Types.INTEGER);
        var gameId = insertOne(insertGame);
        gameIds.put(key, gameId);
        editionIds.put(key, new HashMap<>());
        for (var language : game.path("languages")) {
          insertEdition.setObject(1, gameId);
          insertEdition.setString(2, language.asString(""));
          editionIds.get(key).put(language.asString(""), insertOne(insertEdition));
        }
        for (var entry : game.path("descriptions").properties()) {
          insertDescription.setObject(1, gameId);
          insertDescription.setString(2, entry.getKey());
          insertDescription.setString(3, entry.getValue().asString(""));
          insertDescription.addBatch();
        }
        if (game.hasNonNull("image") && !game.path("image").asString("").isBlank()) {
          insertImage.setObject(1, gameId);
          insertImage.setString(2, game.path("image").asString(""));
          insertImage.addBatch();
        }
        for (var categoryName : game.path("categories")) {
          insertCategoryLink.setObject(1, gameId);
          insertCategoryLink.setInt(2, categoryIds.get(categoryName.asString("")));
          insertCategoryLink.addBatch();
        }
        for (var mechanicName : game.path("mechanics")) {
          insertMechanicLink.setObject(1, gameId);
          insertMechanicLink.setInt(2, mechanicIds.get(mechanicName.asString("")));
          insertMechanicLink.addBatch();
        }
      }
      insertDescription.executeBatch();
      insertImage.executeBatch();
      insertCategoryLink.executeBatch();
      insertMechanicLink.executeBatch();
    }

    long branchGames = 0;
    long branchEditions = 0;
    long rentalCopies = 0;
    try (var insertBranchGame =
            connection.prepareStatement(
                "INSERT INTO branch_games (branch_id, game_id, sale_enabled, rental_enabled, "
                    + "staff_can_teach, rental_hourly_rate_minor) VALUES (?, ?, ?, ?, ?, ?)");
        var insertBranchEdition =
            connection.prepareStatement(
                "INSERT INTO branch_editions (branch_id, edition_id, sale_price_minor, "
                    + "stock_on_hand) VALUES (?, ?, ?, ?)");
        var insertTable =
            connection.prepareStatement(
                "INSERT INTO branch_tables (branch_id, label, seats, hourly_rate_minor) "
                    + "VALUES (?, ?, ?, ?)");
        var insertCopy =
            connection.prepareStatement(
                "INSERT INTO rental_copies (branch_id, game_id, asset_code, operational_status) "
                    + "VALUES (?, ?, ?, 'USABLE')")) {
      var gameRateMinor = data.path("defaults").path("gameRentalRateThb").asInt(20) * 100L;
      var tableRateMinor = data.path("defaults").path("tableRentalRateThb").asInt(30) * 100L;
      for (var branchKey : branchIds.keySet()) {
        var branchId = branchIds.get(branchKey);
        // Every game with retail rows, rental membership or explicit deactivation needs a
        // branch_games row; compute sale/rental flags per branch.
        for (var game : data.path("games")) {
          var key = game.path("key").asString("");
          boolean saleEnabled = hasStockRow(data, branchKey, key);
          var library = data.path("rentalLibraries").path(branchKey).path(key);
          boolean rentalEnabled = library.path("offered").asBoolean(false);
          boolean staffCanTeach = library.path("staffCanTeach").asBoolean(false);
          if (!saleEnabled && library.isMissingNode()) continue;
          insertBranchGame.setObject(1, branchId);
          insertBranchGame.setObject(2, gameIds.get(key));
          insertBranchGame.setBoolean(3, saleEnabled);
          insertBranchGame.setBoolean(4, rentalEnabled);
          insertBranchGame.setBoolean(5, staffCanTeach);
          insertBranchGame.setLong(6, gameRateMinor);
          int updated = insertBranchGame.executeUpdate();
          branchGames += updated;
        }
        for (var stockKey : data.path("retailStock").path(branchKey).properties()) {
          var gameKey = gameKeyOfStock(stockKey.getKey());
          insertBranchEdition.setObject(1, branchId);
          insertBranchEdition.setObject(
              2, editionIds.get(gameKey).get(languageOf(stockKey.getKey())));
          insertBranchEdition.setLong(3, gamePriceMinor(data, gameKey));
          insertBranchEdition.setInt(4, stockKey.getValue().asInt());
          int updated = insertBranchEdition.executeUpdate();
          branchEditions += updated;
        }
        for (var table : data.path("tables").path(branchKey)) {
          insertTable.setObject(1, branchId);
          insertTable.setString(2, table.path("label").asString(""));
          insertTable.setInt(3, table.path("seats").asInt());
          insertTable.setLong(4, tableRateMinor);
          insertTable.addBatch();
        }
        insertTable.executeBatch();
        // Two usable copies per rental-enabled library entry.
        for (var entry : data.path("rentalLibraries").path(branchKey).properties()) {
          if (!entry.getValue().path("offered").asBoolean(false)) continue;
          var gameId = gameIds.get(entry.getKey());
          for (var copy : new int[] {1, 2}) {
            insertCopy.setObject(1, branchId);
            insertCopy.setObject(2, gameId);
            insertCopy.setString(3, "%s-%s-0%d".formatted(entry.getKey(), branchKey, copy));
            insertCopy.addBatch();
            rentalCopies++;
          }
        }
        insertCopy.executeBatch();
      }
    }

    System.out.printf(
        "Seeded %d branches, %d games, %d branch games, %d branch editions, %d tables and "
            + "%d rental copies.%n",
        branchIds.size(),
        gameIds.size(),
        branchGames,
        branchEditions,
        branchIds.size() * 4L,
        rentalCopies);
  }

  /** Fixture stock keys are `<gameKey>-<language>`; the language block may hold a dash. */
  static String gameKeyOfStock(String stockKey) {
    var languages = java.util.Set.of("en", "th");
    for (var language : languages) {
      var suffix = "-" + language;
      if (stockKey.endsWith(suffix))
        return stockKey.substring(0, stockKey.length() - suffix.length());
    }
    return stockKey;
  }

  static String languageOf(String stockKey) {
    var key = gameKeyOfStock(stockKey);
    return key.length() < stockKey.length() ? stockKey.substring(key.length() + 1) : "en";
  }

  static boolean hasStockRow(JsonNode data, String branchKey, String gameKey) {
    return !data.path("retailStock").path(branchKey).path(gameKey + "-en").isMissingNode()
        || !data.path("retailStock").path(branchKey).path(gameKey + "-th").isMissingNode();
  }

  static long gamePriceMinor(JsonNode data, String gameKey) {
    for (var game : data.path("games")) {
      if (game.path("key").asString("").equals(gameKey)) {
        var thb = game.path("retailPriceThb");
        return thb.isNumber() ? thb.asInt() * 100L : 0L;
      }
    }
    return 0L;
  }

  static Map<String, Integer> insertNames(
      java.sql.Connection connection, String table, JsonNode names) throws Exception {
    var ids = new LinkedHashMap<String, Integer>();
    try (var insert =
        connection.prepareStatement(
            "INSERT INTO %s (name) VALUES (?) RETURNING id".formatted(table))) {
      for (var name : names) {
        var value = name.asString("");
        insert.setString(1, value);
        int id = Math.toIntExact(insertOneLong(insert));
        ids.put(value, id);
        String namesTable =
            switch (table) {
              case "game_categories" -> "game_category_names";
              case "game_mechanics" -> "game_mechanic_names";
              default -> throw new IllegalArgumentException("Unsupported taxonomy table: " + table);
            };
        String keyColumn = table.equals("game_categories") ? "category_id" : "mechanic_id";
        try (var label =
            connection.prepareStatement(
                "INSERT INTO "
                    + namesTable
                    + " ("
                    + keyColumn
                    + ", locale, name) VALUES (?, 'en-US', ?)")) {
          label.setInt(1, id);
          label.setString(2, value);
          label.executeUpdate();
        }
      }
    }
    return ids;
  }

  static UUID insertOne(PreparedStatement insert) throws Exception {
    try (ResultSet returned = insert.executeQuery()) {
      returned.next();
      return returned.getObject(1, UUID.class);
    }
  }

  static long insertOneLong(PreparedStatement insert) throws Exception {
    try (ResultSet returned = insert.executeQuery()) {
      returned.next();
      return returned.getLong(1);
    }
  }

  static int port(Map<String, String> env, String name, String fallback) {
    int value = Integer.parseInt(env.getOrDefault(name, fallback));
    if (value < 1 || value > 65535) throw new IllegalArgumentException("Invalid " + name);
    return value;
  }

  static String required(Map<String, String> env, String name) {
    var value = env.get(name);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Set " + name + " in .env.");
    return value;
  }
}
