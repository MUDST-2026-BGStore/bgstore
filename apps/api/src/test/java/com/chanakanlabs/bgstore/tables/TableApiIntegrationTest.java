package com.chanakanlabs.bgstore.tables;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the table administration endpoints against a real PostgreSQL: staff CRUD through the
 * real HTTP path, client rejection, branch scoping, and the wire-value enum status filter.
 */
@SpringBootTest(
    properties = {
      "management.logging.export.otlp.enabled=false",
      "management.otlp.metrics.export.enabled=false",
      "management.tracing.export.enabled=false",
      "spring.data.redis.password=test-password"
    })
@AutoConfigureMockMvc
@Testcontainers
class TableApiIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.4-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "test-password");

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "test-password");
  }

  private static final String STAFF = "table-staff";
  private static final String CLIENT = "table-client";
  private static final long TABLE_ID = 9701L;
  private static final String TABLE_NAME = "Api Test Table";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate database;

  @BeforeEach
  void seed() {
    for (String subject : List.of(STAFF, CLIENT)) {
      database.update("delete from staff_branch_assignment where staff_subject = ?", subject);
      database.update("delete from client_profiles where subject = ?", subject);
      database.update("delete from identity_accounts where subject = ?", subject);
    }
    database.update("delete from table_reservation where table_id = ?", TABLE_ID);
    database.update("delete from store_table where name = ?", TABLE_NAME);

    account(STAFF, "STAFF");
    account(CLIENT, "CLIENT");
    database.update(
        """
        insert into staff_branch_assignment (staff_subject, branch_id)
        select ?, id from branch
        on conflict (staff_subject, branch_id) do nothing
        """,
        STAFF);
    table(TABLE_ID, TABLE_NAME, "Silom");
  }

  @Test
  void staffListsTablesAndFiltersByTheWireEnumStatus() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/tables?branch=Silom&status=Available&search=" + TABLE_NAME)
                .with(staffLogin(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value((int) TABLE_ID))
        .andExpect(jsonPath("$.items[0].name").value(TABLE_NAME))
        .andExpect(jsonPath("$.items[0].status").value("Available"));
  }

  @Test
  void aClientCannotListTables() throws Exception {
    mockMvc
        .perform(get("/api/v1/tables").with(clientLogin(CLIENT)))
        .andExpect(status().isForbidden());
  }

  @Test
  void staffCreatesUpdatesAndDeletesATableThroughTheApi() throws Exception {
    String body =
        "{\"name\":\"Created Table\",\"branch\":\"Silom\",\"capacity\":6,"
            + "\"shape\":\"Round\",\"status\":\"Available\",\"active\":true,\"zone\":\"Terrace\"}";

    String created =
        mockMvc
            .perform(
                post("/api/v1/tables")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf())
                    .with(staffLogin(STAFF)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Created Table"))
            .andExpect(jsonPath("$.capacity").value(6))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = new tools.jackson.databind.ObjectMapper().readTree(created).path("id").asLong();

    mockMvc
        .perform(
            put("/api/v1/tables/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Created Table\",\"branch\":\"Silom\",\"capacity\":6,"
                        + "\"shape\":\"Round\",\"status\":\"Occupied\",\"active\":true,"
                        + "\"zone\":\"Terrace\"}")
                .with(csrf())
                .with(staffLogin(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Occupied"));

    mockMvc
        .perform(delete("/api/v1/tables/" + id).with(csrf()).with(staffLogin(STAFF)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/tables/" + id).with(staffLogin(STAFF)))
        .andExpect(status().isNotFound());
  }

  @Test
  void aDuplicateTableNameInTheSameBranchIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\""
                        + TABLE_NAME
                        + "\",\"branch\":\"Silom\",\"capacity\":2,"
                        + "\"shape\":\"Round\",\"status\":\"Available\",\"active\":true,"
                        + "\"zone\":\"Main Hall\"}")
                .with(csrf())
                .with(staffLogin(STAFF)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void staffCannotReadTablesOutsideTheirAssignedBranches() throws Exception {
    database.update(
        """
        delete from staff_branch_assignment
        where staff_subject = ?
          and branch_id <> (select id from branch where name = 'Silom')
        """,
        STAFF);
    long sukhumvitTable = TABLE_ID + 1;
    table(sukhumvitTable, TABLE_NAME + " Sukhumvit", "Sukhumvit");

    mockMvc
        .perform(get("/api/v1/tables/" + sukhumvitTable).with(staffLogin(STAFF)))
        .andExpect(status().isForbidden());
  }

  private void account(String subject, String role) {
    database.update(
        """
        insert into identity_accounts (subject, username, email, first_name, last_name, application_role)
        values (?, ?, ?, 'Local', 'Account', ?)
        """,
        subject,
        subject,
        subject + "@example.test",
        role);
    if ("CLIENT".equals(role)) {
      database.update(
          """
          insert into client_profiles (subject, phone_e164, first_name, last_name, completed_at)
          values (?, '+66812345678', 'Local', 'Account', current_timestamp)
          """,
          subject);
    }
  }

  private void table(long id, String name, String branch) {
    database.update(
        """
        insert into store_table (id, name, branch_id, capacity, shape, status, active, zone, last_updated)
        values (?, ?, (select id from branch where name = ?), 4, 'Round', 'Available', true, 'Main Hall', current_timestamp)
        """,
        id,
        name,
        branch);
  }

  private static OidcLoginRequestPostProcessor clientLogin(String subject) {
    return login(subject, "CLIENT", "Client");
  }

  private static OidcLoginRequestPostProcessor staffLogin(String subject) {
    return login(subject, "STAFF", "Staff");
  }

  private static OidcLoginRequestPostProcessor login(
      String subject, String role, String familyName) {
    return oidcLogin()
        .idToken(
            token ->
                token.claims(
                    claims ->
                        claims.putAll(
                            Map.of(
                                "sub",
                                subject,
                                "preferred_username",
                                subject,
                                "email",
                                subject + "@example.test",
                                "given_name",
                                "Local",
                                "family_name",
                                familyName,
                                "realm_access",
                                Map.of("roles", List.of(role))))));
  }
}
