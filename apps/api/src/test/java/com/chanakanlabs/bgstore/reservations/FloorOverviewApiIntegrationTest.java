package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exercises the floor overview against a real PostgreSQL, reservations schema from Hibernate. */
@SpringBootTest(
    properties = {
      "management.logging.export.otlp.enabled=false",
      "management.otlp.metrics.export.enabled=false",
      "management.tracing.export.enabled=false",
      "spring.data.redis.password=test-password"
    })
@AutoConfigureMockMvc
@Testcontainers
class FloorOverviewApiIntegrationTest {

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

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate database;

  @BeforeEach
  void seedFloor() {
    assignStaffToEveryBranch("floor-staff");
    database.execute("delete from table_reservation");
    database.execute("delete from store_table");
    table(1, "Table 1", "Silom", 4, "Round", "Available");
    table(2, "Table 2", "Silom", 6, "Square", "Occupied");
    table(3, "Table 3", "Silom", 6, "Round", "Reserved");
    table(13, "Table 13", "Silom", 4, "Round", "Available");
    table(4, "Window seat", "Sukhumvit", 2, "Round", "Available");
    // Out of service but left Available: it is not on the floor at all.
    table(20, "Broken table", "Silom", 4, "Round", "Available");
    database.update("update store_table set active = false where id = 20");
  }

  @Test
  void anonymousUserCannotReachTheFloorOverview() throws Exception {
    mockMvc.perform(get("/api/v1/floor-overview")).andExpect(status().isUnauthorized());
  }

  @Test
  void countsTheBranchFloorWhileTheStatusFilterNarrowsTheRows() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/floor-overview")
                .param("branch", "Silom")
                .param("status", "Available")
                .with(staffLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counts.available").value(2))
        .andExpect(jsonPath("$.counts.occupied").value(1))
        .andExpect(jsonPath("$.counts.reserved").value(1))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(1))
        .andExpect(jsonPath("$.items[1].id").value(13));
  }

  @Test
  void pagesTheTablesAndAttachesTheirUpcomingSlotsSoonestFirst() throws Exception {
    reservation(2, "now() + interval '3 hours'", "now() + interval '4 hours'");
    reservation(2, "now() - interval '3 hours'", "now() - interval '1 hour'");
    reservation(2, "now() + interval '1 hour'", "now() + interval '2 hours'");
    reservation(4, "now() + interval '1 hour'", "now() + interval '2 hours'");

    var body =
        read(
            mockMvc
                .perform(
                    get("/api/v1/floor-overview")
                        .param("branch", "Silom")
                        .param("pageSize", "2")
                        .with(staffLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].reservedSlots.length()").value(0))
                .andExpect(jsonPath("$.items[1].name").value("Table 2"))
                .andExpect(jsonPath("$.items[1].capacity").value(6))
                .andExpect(jsonPath("$.items[1].shape").value("Square"))
                .andExpect(jsonPath("$.items[1].status").value("Occupied"))
                .andExpect(jsonPath("$.items[1].reservedSlots.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString());

    var slots = body.get("items").get(1).get("reservedSlots");
    var first = OffsetDateTime.parse(slots.get(0).get("startsAt").asString());
    var second = OffsetDateTime.parse(slots.get(1).get("startsAt").asString());
    assertThat(first).isBefore(second);
  }

  @Test
  void findsATableByItsId() throws Exception {
    mockMvc
        .perform(get("/api/v1/floor-overview").param("search", "4").with(staffLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Window seat"));
  }

  @Test
  void explainsWhyAvailabilityIsRejectedOutsideOpeningHours() throws Exception {
    table(30, "Big table", "Big C Rama I", 6, "Round", "Available");

    mockMvc
        .perform(availability("09:00", "12:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10:00–20:00")));
  }

  @Test
  void listsTablesThatFitThePartyWithinOpeningHours() throws Exception {
    table(30, "Big table", "Big C Rama I", 6, "Round", "Available");
    table(31, "Small table", "Big C Rama I", 2, "Round", "Available");

    mockMvc
        .perform(availability("10:00", "13:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tables.length()").value(1))
        .andExpect(jsonPath("$.tables[0].id").value(30))
        .andExpect(jsonPath("$.tables[0].available").value(true));
  }

  private org.springframework.test.web.servlet.RequestBuilder availability(
      String startTime, String endTime) {
    var date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bangkok")).plusDays(1);
    return get("/api/v1/reservations/availability")
        .param("branch", "Big C Rama I")
        .param("date", date.toString())
        .param("startTime", startTime)
        .param("endTime", endTime)
        .param("partySize", "5")
        .with(staffLogin());
  }

  private void table(
      long id, String name, String branch, int capacity, String shape, String status) {
    database.update(
        """
        insert into store_table (id, name, branch_id, capacity, shape, status, active, last_updated)
        values (?, ?, (select id from branch where name = ?), ?, ?, ?, true, current_timestamp)
        """,
        id,
        name,
        branch,
        capacity,
        shape,
        status);
  }

  private void assignStaffToEveryBranch(String staffSubject) {
    database.update(
        """
        insert into staff_branch_assignment (staff_subject, branch_id)
        select ?, id from branch
        on conflict (staff_subject, branch_id) do nothing
        """,
        staffSubject);
  }

  private void reservation(long tableId, String startsAt, String endsAt) {
    database.update(
        "insert into table_reservation (table_id, starts_at, ends_at) values (?, "
            + startsAt
            + ", "
            + endsAt
            + ")",
        tableId);
  }

  private JsonNode read(String body) {
    return json.readTree(body);
  }

  private static OidcLoginRequestPostProcessor staffLogin() {
    return SecurityMockMvcRequestPostProcessors.oidcLogin()
        .idToken(
            token ->
                token.claims(
                    claims ->
                        claims.putAll(
                            Map.of(
                                "sub", "floor-staff",
                                "preferred_username", "staff@example.test",
                                "email", "staff@example.test",
                                "given_name", "Local",
                                "family_name", "Staff",
                                "realm_access", Map.of("roles", List.of("STAFF"))))));
  }
}
