package com.chanakanlabs.theparlor.tools;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class ReferenceDataSeedTests {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3");

  @Test
  void seedsCompleteDiscoveryMetadataAndCanBeRepeated() throws Exception {
    org.flywaydb.core.Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("filesystem:src/main/resources/db/migration")
        .load()
        .migrate();
    try (var db =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      try (var statement = db.createStatement()) {
        statement.execute(
            "INSERT INTO accounts(first_name,last_name,phone_number,preferences) VALUES ('Seed','Owner','123','{\"preferredLocale\":\"en-US\"}')");
      }
      db.setAutoCommit(false);
      for (int run = 0; run < 2; run++) {
        ReferenceDataSeed.run(db);
        db.commit();
        try (var statement = db.createStatement()) {
          try (var rows = statement.executeQuery("SELECT count(*) FROM games")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(16);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*) FROM games WHERE name='Blood on the Clocktower'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*) FROM games WHERE name='Thirsty Sword Lesbians'")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*) FROM game_categories c LEFT JOIN game_category_names n ON c.id=n.category_id AND n.locale='en-US' WHERE n.name IS NULL")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT designer,difficulty,teach_minutes,setup_minutes FROM games WHERE name='Wavelength'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isNull();
            assertThat(rows.getString(2)).isEqualTo("EASY");
            assertThat(rows.getInt(3)).isEqualTo(5);
            assertThat(rows.getInt(4)).isEqualTo(2);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*) FROM games WHERE difficulty IS NULL OR teach_minutes IS NULL OR setup_minutes IS NULL")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
          }
          try (var rows = statement.executeQuery("SELECT count(DISTINCT difficulty) FROM games")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(3);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*) FROM game_descriptions WHERE locale='th-TH'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(16);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*) FROM games g WHERE EXISTS (SELECT 1 FROM game_images i WHERE i.game_id=g.id AND i.url <> '')")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(16);
          }
          try (var rows =
              statement.executeQuery("SELECT count(*) FROM accounts WHERE first_name='Seed'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
          }
        }
      }
    }
  }
}
