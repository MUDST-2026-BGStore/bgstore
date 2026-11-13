package com.chanakanlabs.theparlor.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class SchemaIntegrityTests {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3");
  Connection connection;
  JdbcTemplate jdbc;

  @BeforeEach
  void schema() throws Exception {
    String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    connection.createStatement().execute("SET search_path TO " + schema);
    jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
  }

  @AfterEach
  void close() throws Exception {
    if (connection != null) connection.close();
  }

  UUID branch() {
    return jdbc.queryForObject(
        "INSERT INTO branches(name,address) VALUES ('Branch','Address') RETURNING id", UUID.class);
  }

  UUID table(UUID branch) {
    return jdbc.queryForObject(
        "INSERT INTO branch_tables(branch_id,label,seats) VALUES (?, 'Table',4) RETURNING id",
        UUID.class,
        branch);
  }

  UUID reservation(UUID branch, UUID table) {
    return jdbc.queryForObject(
        "INSERT INTO reservations(branch_id,table_id,guest_name,source,status,starts_at,ends_at,party_size,table_hourly_rate_minor,created_by_subject) VALUES (?,?,'Guest','STAFF_BOOKING','CONFIRMED',now(),now()+interval '1 hour',1,0,'staff') RETURNING id",
        UUID.class,
        branch,
        table);
  }

  @Test
  void freshBaselineHasRequiredAccountFieldsAndNoLegacyNameColumn() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version='001'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='accounts'",
                String.class))
        .contains("first_name", "last_name")
        .doesNotContain("display_name", "preferred_locale");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO accounts(first_name,last_name,phone_number,preferences) VALUES (null,'Last','123','{\"preferredLocale\":\"en-US\"}')"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO sale_stock_adjustments(branch_id,edition_id,delta,reason,operator_subject) VALUES (null,null,1,'Receipt','staff')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void localeMigrationPreservesTheAuthoritativeColumnAndOtherPreferences() throws Exception {
    String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
    var configuration =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    configuration.target("001").load().migrate();
    jdbc.execute("SET search_path TO " + schema);
    jdbc.update(
        """
        INSERT INTO accounts(first_name,last_name,phone_number,preferred_locale,preferences)
        VALUES ('First','Last','123','en-US',
          '{"preferredLocale":"th-TH","theme":"dark","nested":{"enabled":true}}'),
          ('Other','User','456','th-TH','{}')
        """);

    configuration.target(org.flywaydb.core.api.MigrationVersion.LATEST).load().migrate();

    assertThat(
            jdbc.queryForObject(
                """
        SELECT preferences = '{"preferredLocale":"en-US","theme":"dark","nested":{"enabled":true}}'::jsonb
        FROM accounts WHERE first_name='First'
        """,
                Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT preferences->>'preferredLocale' FROM accounts WHERE first_name='Other'",
                String.class))
        .isEqualTo("th-TH");
    assertThat(
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='accounts'",
                String.class))
        .doesNotContain("preferred_locale");
  }

  @Test
  void emptyDevelopmentSchemaAtV003MigratesToLatest() {
    String schema = "empty_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var configuration =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    configuration.target("003").load().migrate();

    configuration.target(org.flywaydb.core.api.MigrationVersion.LATEST).load().migrate();

    jdbc.execute("SET search_path TO " + schema);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version='009'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void rejectsDevelopmentOrdersCreatedBeforePaymentCutover() {
    String schema = "order_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var configuration =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    configuration.target("005").load().migrate();
    jdbc.execute("SET search_path TO " + schema);
    var branch = branch();
    jdbc.update(
        """
        INSERT INTO orders(
          branch_id, contact_name, contact_email, contact_phone, source, status,
          fulfillment_method, total_minor, created_by_subject
        ) VALUES (?, 'Development order', 'dev@example.invalid', '000', 'CUSTOMER',
                  'PLACED', 'PICKUP', 100, 'development-subject')
        """,
        branch);

    assertThatThrownBy(
            () ->
                configuration
                    .target(org.flywaydb.core.api.MigrationVersion.LATEST)
                    .load()
                    .migrate())
        .hasStackTraceContaining(
            "V006 requires an empty orders table; reset the development database");
  }

  @Test
  void rejectsDevelopmentReservationsCreatedBeforeLifecycleCutover() {
    String schema = "reservation_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var configuration =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    configuration.target("006").load().migrate();
    jdbc.execute("SET search_path TO " + schema);
    var branch = branch();
    reservation(branch, table(branch));

    assertThatThrownBy(
            () ->
                configuration
                    .target(org.flywaydb.core.api.MigrationVersion.LATEST)
                    .load()
                    .migrate())
        .hasStackTraceContaining(
            "V007 requires an empty reservations table; reset the development database");
  }

  @Test
  void rejectsMissingNullAndUnsupportedPreferenceLocales() {
    for (String preferences :
        java.util.List.of(
            "{}",
            "{\"preferredLocale\":null}",
            "{\"preferredLocale\":\"en-TH\"}",
            "{\"preferredLocale\":123}")) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO accounts(first_name,last_name,phone_number,preferences) VALUES ('First','Last','123',?::jsonb)",
                      preferences))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void reservationCannotUseAnotherBranchesTable() {
    var a = branch();
    var b = branch();
    var table = table(a);
    assertThat(reservation(a, table)).isNotNull();
    assertThatThrownBy(() -> reservation(b, table))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void allocationReferencesRentalAndCopyInTheSameBranchAndCascadesOnRentalDeletion() {
    var a = branch();
    var b = branch();
    var reservation = reservation(a, table(a));
    var rental =
        jdbc.queryForObject(
            "INSERT INTO rentals(branch_id,reservation_id,started_at,planned_ends_at,checked_in_by) VALUES (?,?,now(),now()+interval '1 hour','staff') RETURNING id",
            UUID.class,
            a,
            reservation);
    var game =
        jdbc.queryForObject(
            "INSERT INTO games(name,min_players,max_players,play_minutes_min,play_minutes_max) VALUES ('Game',1,4,10,20) RETURNING id",
            UUID.class);
    var copy =
        jdbc.queryForObject(
            "INSERT INTO rental_copies(branch_id,game_id,asset_code) VALUES (?,?,'Copy A') RETURNING id",
            UUID.class,
            a,
            game);
    var otherCopy =
        jdbc.queryForObject(
            "INSERT INTO rental_copies(branch_id,game_id,asset_code) VALUES (?,?,'Copy B') RETURNING id",
            UUID.class,
            b,
            game);
    jdbc.update(
        "INSERT INTO rental_copy_allocations(rental_id,branch_id,copy_id) VALUES (?,?,?)",
        rental,
        a,
        copy);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO rental_copy_allocations(rental_id,branch_id,copy_id) VALUES (?,?,?)",
                    rental,
                    b,
                    otherCopy))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update("DELETE FROM rentals WHERE id=?", rental);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM rental_copy_allocations", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM rental_copies", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void rentalCannotUseAnotherBranchesReservation() {
    var a = branch();
    var b = branch();
    var reservation = reservation(a, table(a));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO rentals(branch_id,reservation_id,started_at,planned_ends_at,checked_in_by) VALUES (?,?,now(),now()+interval '1 hour','staff')",
                    b,
                    reservation))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
