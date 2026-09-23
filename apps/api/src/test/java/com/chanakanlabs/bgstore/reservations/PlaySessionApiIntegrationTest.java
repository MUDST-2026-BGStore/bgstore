package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

/**
 * Exercises the play-session lifecycle against a real PostgreSQL and the migrated schema.
 *
 * <p>The fee is confirmed by staff at check-out, so these tests cover the whole path: a client
 * reads its checked-in session, asks for assistance, and staff check in and check out.
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
class PlaySessionApiIntegrationTest {

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

  private static final String CLIENT = "play-session-client";
  private static final String OTHER_CLIENT = "someone-else";
  private static final String STAFF = "session-staff";
  private static final String TABLE_NAME = "Play Session Table";
  private static final long TABLE_ID = 9501L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate database;

  @BeforeEach
  void seed() {
    database.execute("delete from payment");
    database.execute("delete from session_assistance_request");
    database.execute("delete from table_reservation");
    database.execute("delete from reservation");
    for (String subject : List.of(CLIENT, OTHER_CLIENT, STAFF)) {
      database.update("delete from staff_branch_assignment where staff_subject = ?", subject);
      database.update("delete from client_profiles where subject = ?", subject);
      database.update("delete from identity_accounts where subject = ?", subject);
    }
    database.update("delete from store_table where name = ?", TABLE_NAME);

    account(CLIENT, "CLIENT");
    account(OTHER_CLIENT, "CLIENT");
    account(STAFF, "STAFF");
    assignStaffToEveryBranch(STAFF);
    table(TABLE_ID, TABLE_NAME, "Silom");
  }

  @Test
  void anonymousUserCannotReachTheActiveSession() throws Exception {
    mockMvc.perform(get("/api/v1/me/active-session")).andExpect(status().isUnauthorized());
  }

  @Test
  void clientWithoutACheckedInSessionGetsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/active-session").with(clientLogin(CLIENT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void clientReadsTheActiveSessionItsReservationRepresents() throws Exception {
    reservation("res-active", CLIENT, "CheckedIn", Instant.now().minus(Duration.ofMinutes(105)));

    mockMvc
        .perform(get("/api/v1/me/active-session").with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservationId").value("res-active"))
        .andExpect(jsonPath("$.locationName").value("Silom"))
        .andExpect(jsonPath("$.tableName").value(TABLE_NAME))
        .andExpect(jsonPath("$.partySize").value(4))
        .andExpect(jsonPath("$.ratePerHour").value(120))
        .andExpect(jsonPath("$.accruedAmount").value(0))
        .andExpect(jsonPath("$.currency").value("THB"));
  }

  @Test
  void staffCheckInStartsPlayAndTheClientSeesTheSession() throws Exception {
    reservation("res-checkin", CLIENT, "Reserved", null);

    mockMvc
        .perform(
            post("/api/v1/reservations/res-checkin/check-in").with(csrf()).with(staffLogin(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("res-checkin"))
        .andExpect(jsonPath("$.status").value("CheckedIn"));

    mockMvc
        .perform(get("/api/v1/me/active-session").with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservationId").value("res-checkin"));
  }

  @Test
  void aClientCannotCheckInOrCheckOutItsOwnSession() throws Exception {
    reservation("res-authz", CLIENT, "Reserved", null);

    mockMvc
        .perform(
            post("/api/v1/reservations/res-authz/check-in").with(csrf()).with(clientLogin(CLIENT)))
        .andExpect(status().isForbidden());
  }

  @Test
  void aClientCannotReadAnotherClientsActiveSession() throws Exception {
    reservation("res-other", OTHER_CLIENT, "CheckedIn", Instant.now().minus(Duration.ofHours(1)));

    mockMvc
        .perform(get("/api/v1/me/active-session").with(clientLogin(CLIENT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void staffCheckOutRecordsTheConfirmedFeeAndClosesTheSession() throws Exception {
    reservation("res-checkout", CLIENT, "CheckedIn", Instant.now().minus(Duration.ofHours(3)));

    mockMvc
        .perform(
            post("/api/v1/reservations/res-checkout/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"finalAmount\":240,\"paymentMethod\":\"Cash\"}")
                .with(csrf())
                .with(staffLogin(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservationId").value("res-checkout"))
        .andExpect(jsonPath("$.totalDue").value(240))
        .andExpect(jsonPath("$.paymentMethod").value("Cash"))
        .andExpect(jsonPath("$.hours").value(3))
        .andExpect(jsonPath("$.currency").value("THB"))
        .andExpect(jsonPath("$.payment.gateway").value("bogus"))
        .andExpect(
            jsonPath("$.payment.reference").value(org.hamcrest.Matchers.startsWith("bogus-")));

    assertThat(reservationStatus("res-checkout")).isEqualTo("Completed");
    assertThat(paymentMethod("res-checkout")).isEqualTo("Cash");
    assertThat(paymentCount("res-checkout")).isEqualTo(1);
    mockMvc
        .perform(get("/api/v1/me/active-session").with(clientLogin(CLIENT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void staffCheckOutCanWaiveTheFee() throws Exception {
    reservation("res-waived", CLIENT, "CheckedIn", Instant.now().minus(Duration.ofHours(1)));

    mockMvc
        .perform(
            post("/api/v1/reservations/res-waived/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"finalAmount\":0,\"paymentMethod\":\"Waived\"}")
                .with(csrf())
                .with(staffLogin(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDue").value(0))
        .andExpect(jsonPath("$.paymentMethod").value("Waived"))
        .andExpect(jsonPath("$.payment").doesNotExist());
    assertThat(paymentCount("res-waived")).isZero();
  }

  @Test
  void aWaivedCheckOutCannotCarryAnAmount() throws Exception {
    reservation("res-badwaive", CLIENT, "CheckedIn", Instant.now().minus(Duration.ofHours(1)));

    mockMvc
        .perform(
            post("/api/v1/reservations/res-badwaive/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"finalAmount\":100,\"paymentMethod\":\"Waived\"}")
                .with(csrf())
                .with(staffLogin(STAFF)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void assistanceIsRecordedOncePerRequestId() throws Exception {
    reservation("res-help", CLIENT, "CheckedIn", Instant.now().minus(Duration.ofMinutes(30)));
    UUID requestId = UUID.randomUUID();
    String body = "{\"kind\":\"CallStaff\",\"requestId\":\"" + requestId + "\"}";

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              post("/api/v1/reservations/res-help/assistance")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf())
                  .with(clientLogin(CLIENT)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.requestId").value(requestId.toString()))
          .andExpect(jsonPath("$.kind").value("CallStaff"))
          .andExpect(jsonPath("$.status").value("Recorded"));
    }

    assertThat(assistanceCount(requestId)).isEqualTo(1);
    assertThat(reservationStatus("res-help")).isEqualTo("CheckedIn");
  }

  @Test
  void assistanceRequiresTheClientsOwnActiveSession() throws Exception {
    reservation("res-notmine", OTHER_CLIENT, "CheckedIn", Instant.now().minus(Duration.ofHours(1)));

    mockMvc
        .perform(
            post("/api/v1/reservations/res-notmine/assistance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"EndPlaying\",\"requestId\":\"" + UUID.randomUUID() + "\"}")
                .with(csrf())
                .with(clientLogin(CLIENT)))
        .andExpect(status().isNotFound());
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

  private void assignStaffToEveryBranch(String staffSubject) {
    database.update(
        """
        insert into staff_branch_assignment (staff_subject, branch_id)
        select ?, id from branch
        on conflict (staff_subject, branch_id) do nothing
        """,
        staffSubject);
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

  /** Inserts a reservation in Silom; a null check-in instant means play has not started. */
  private void reservation(String id, String clientSubject, String status, Instant checkInTime) {
    reservation(id, clientSubject, status, checkInTime, "Silom");
  }

  private void reservation(
      String id, String clientSubject, String status, Instant checkInTime, String branch) {
    database.update(
        """
        insert into reservation (
            id, client_subject, branch_id, title, reservation_date, time_slot, party_size,
            table_id, table_name, seats, rate_per_hour, status, customer_name, phone_number,
            check_in_time, actual_check_out, overtime_minutes, total_price, can_cancel)
        values (
            ?, ?, (select id from branch where name = ?), ?, ?, ?, 4,
            9501, ?, 4, 120, ?, ?, ?, ?, '-', 0, 0, true)
        """,
        id,
        clientSubject,
        branch,
        "Table " + TABLE_NAME,
        "2026-09-22",
        "09:00–12:00",
        TABLE_NAME,
        status,
        "Session Client",
        "0123456789",
        checkInTime == null ? "-" : checkInTime.toString());
  }

  @Test
  void staffSeeOnlySessionsInTheirAssignedBranches() throws Exception {
    reservation("res-silom", CLIENT, "Reserved", null, "Silom");
    reservation("res-sukhumvit", OTHER_CLIENT, "Reserved", null, "Sukhumvit");
    // Narrow the seeded assignment down to one branch.
    database.update(
        """
        delete from staff_branch_assignment
        where staff_subject = ?
          and branch_id <> (select id from branch where name = 'Silom')
        """,
        STAFF);

    mockMvc
        .perform(get("/api/v1/sessions").with(staffLogin(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value("res-silom"));
  }

  @Test
  void theQueueOnlyCarriesReservationsWaitingToStartOrInPlay() throws Exception {
    reservation("res-done", CLIENT, "Completed", Instant.now().minus(Duration.ofHours(4)));
    reservation("res-waiting", OTHER_CLIENT, "Reserved", null);

    mockMvc
        .perform(get("/api/v1/sessions").with(staffLogin(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value("res-waiting"));
  }

  @Test
  void aClientCannotListTheStaffSessionQueue() throws Exception {
    mockMvc
        .perform(get("/api/v1/sessions").with(clientLogin(CLIENT)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousUserCannotReachTheSessionQueue() throws Exception {
    mockMvc.perform(get("/api/v1/sessions")).andExpect(status().isUnauthorized());
  }

  private String reservationStatus(String id) {
    return database.queryForObject("select status from reservation where id = ?", String.class, id);
  }

  private String paymentMethod(String id) {
    return database.queryForObject(
        "select payment_method from reservation where id = ?", String.class, id);
  }

  private int paymentCount(String reservationId) {
    Integer count =
        database.queryForObject(
            "select count(*) from payment where reservation_id = ?", Integer.class, reservationId);
    return count == null ? 0 : count;
  }

  private int assistanceCount(UUID requestId) {
    Integer count =
        database.queryForObject(
            "select count(*) from session_assistance_request where request_id = ?",
            Integer.class,
            requestId);
    return count == null ? 0 : count;
  }

  private static OidcLoginRequestPostProcessor clientLogin(String subject) {
    return SecurityMockMvcRequestPostProcessors.oidcLogin()
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
                                "Session",
                                "family_name",
                                "Client",
                                "realm_access",
                                Map.of("roles", List.of("CLIENT"))))));
  }

  private static OidcLoginRequestPostProcessor staffLogin(String subject) {
    return SecurityMockMvcRequestPostProcessors.oidcLogin()
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
                                "Session",
                                "family_name",
                                "Staff",
                                "realm_access",
                                Map.of("roles", List.of("STAFF"))))));
  }
}
