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
              statement.executeQuery(
                  "SELECT count(*), count(*) FILTER (WHERE names -> 'th-TH' IS NOT NULL) FROM menu_items")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(16);
            assertThat(rows.getInt(2)).isEqualTo(16);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*), count(DISTINCT branch_id), sum(held) FROM branch_menu_stock")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(48);
            assertThat(rows.getInt(2)).isEqualTo(3);
            assertThat(rows.getInt(3)).isZero();
          }
          // Both demo paths are ready for immediate check-in, even when seeding outside
          // opening hours. Repeating the seed replaces bookings rather than accumulating them.
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*), count(DISTINCT branch_id), "
                      + "count(*) FILTER (WHERE status='CONFIRMED' AND starts_at <= now() "
                      + "AND ends_at >= now() + interval '11 hours'), "
                      + "count(*) FILTER (WHERE guest_name IS NOT NULL AND customer_id IS NULL) "
                      + "FROM reservations")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(6);
            assertThat(rows.getInt(2)).isEqualTo(3);
            assertThat(rows.getInt(3)).isEqualTo(6);
            assertThat(rows.getInt(4)).isEqualTo(6);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*), count(DISTINCT h.reservation_id) "
                      + "FROM reservation_copy_holds h "
                      + "JOIN rental_copies c ON c.id=h.copy_id AND c.branch_id=h.branch_id "
                      + "JOIN reservation_games g ON g.reservation_id=h.reservation_id "
                      + "AND g.game_id=c.game_id "
                      + "JOIN branch_games bg ON bg.branch_id=h.branch_id AND bg.game_id=c.game_id "
                      + "WHERE c.operational_status='USABLE' AND bg.rental_enabled "
                      + "AND g.quantity=1 AND g.unit_hourly_rate_minor=bg.rental_hourly_rate_minor")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(3);
            assertThat(rows.getInt(2)).isEqualTo(3);
          }
          try (var rows = statement.executeQuery("SELECT count(*) FROM reservation_games")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(3);
          }
          try (var rows =
              statement.executeQuery(
                  "SELECT count(*) FROM reservations r JOIN branch_tables t "
                      + "ON t.branch_id=r.branch_id AND t.id=r.table_id "
                      + "WHERE r.party_size <= t.seats "
                      + "AND r.table_hourly_rate_minor=t.hourly_rate_minor")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(6);
          }
          try (var rows = statement.executeQuery("SELECT count(*) FROM rentals")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
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
