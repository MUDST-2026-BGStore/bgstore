package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the reservation lifecycle against a real PostgreSQL and the migrated schema: a client
 * books a table through the API, sees it in the history list, cancels it, and the freed slot can be
 * booked again. Also covers the staff walk-in booking path and the 409 double-booking guard.
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
class ReservationApiIntegrationTest {

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

  private static final String CLIENT = "reservation-client";
  private static final String OTHER_CLIENT = "reservation-other";
  private static final String STAFF = "reservation-staff";
  private static final long TABLE_ID = 9601L;
  private static final String TABLE_NAME = "Reservation Test Table";
  private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

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
  void clientCreatesAReservationThroughTheApi() throws Exception {
    createReservation(CLIENT, TABLE_ID)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.status").value("Reserved"))
        .andExpect(jsonPath("$.date").value(tomorrow().toString()))
        .andExpect(jsonPath("$.timeSlot").value("14:00–16:00"))
        .andExpect(jsonPath("$.partySize").value(2))
        .andExpect(jsonPath("$.tableId").value((int) TABLE_ID))
        .andExpect(jsonPath("$.tableName").value(TABLE_NAME))
        .andExpect(jsonPath("$.customerName").value("Local Client"))
        .andExpect(jsonPath("$.canCancel").value(true));

    Integer bookedTables =
        database.queryForObject("select count(*) from table_reservation", Integer.class);
    assertThat(bookedTables).isEqualTo(1);
  }

  @Test
  void bookingTheSameTableTwiceForTheSameSlotIsA409() throws Exception {
    createReservation(CLIENT, TABLE_ID).andExpect(status().isCreated());
    createReservation(OTHER_CLIENT, TABLE_ID).andExpect(status().isConflict());
  }

  @Test
  void availabilityFlagsTheBookedTableAndFreesItAfterCancellation() throws Exception {
    String id =
        createdId(createReservation(CLIENT, TABLE_ID).andExpect(status().isCreated()).andReturn());

    mockMvc
        .perform(availabilityRequest().with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.tables[?(@.id == " + TABLE_ID + ")].available")
                .value(org.hamcrest.Matchers.contains(false)));

    mockMvc
        .perform(
            post("/api/v1/reservations/" + id + "/cancel").with(csrf()).with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Cancelled"))
        .andExpect(jsonPath("$.canCancel").value(false));

    // The released slot must be bookable again, not stuck as a phantom booking.
    createReservation(OTHER_CLIENT, TABLE_ID).andExpect(status().isCreated());
  }

  @Test
  void availabilityDistinguishesFreeAndBookedTables() throws Exception {
    long freeTable = TABLE_ID + 1;
    table(freeTable, TABLE_NAME + " Free", "Silom");

    createReservation(CLIENT, TABLE_ID).andExpect(status().isCreated());

    mockMvc
        .perform(availabilityRequest().with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.tables[?(@.id == " + TABLE_ID + ")].available")
                .value(org.hamcrest.Matchers.contains(false)))
        .andExpect(
            jsonPath("$.tables[?(@.id == " + freeTable + ")].available")
                .value(org.hamcrest.Matchers.contains(true)));
  }

  @Test
  void aClientListsItsOwnReservationsWithPagination() throws Exception {
    String mine =
        createdId(createReservation(CLIENT, TABLE_ID).andExpect(status().isCreated()).andReturn());
    long secondTable = TABLE_ID + 2;
    table(secondTable, TABLE_NAME + " Two", "Silom");
    createReservation(CLIENT, secondTable, "18:00", "20:00").andExpect(status().isCreated());
    createReservation(OTHER_CLIENT, TABLE_ID, "10:00", "12:00").andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/reservations").with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.page").value(1));

    mockMvc
        .perform(get("/api/v1/reservations?page=1&pageSize=1").with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.pageSize").value(1))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items.length()").value(1));

    // Cancel one and the status filter must actually filter (the wire value is "Reserved").
    mockMvc
        .perform(
            post("/api/v1/reservations/" + mine + "/cancel").with(csrf()).with(clientLogin(CLIENT)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/reservations?status=Reserved").with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));

    mockMvc
        .perform(get("/api/v1/reservations?status=Cancelled").with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void aClientReadsItsOwnReservationButNotAnotherClients() throws Exception {
    String mine =
        createdId(createReservation(CLIENT, TABLE_ID).andExpect(status().isCreated()).andReturn());

    mockMvc
        .perform(get("/api/v1/reservations/" + mine).with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(mine))
        .andExpect(jsonPath("$.phoneNumber").value("0123456789"));

    mockMvc
        .perform(get("/api/v1/reservations/" + mine).with(clientLogin(OTHER_CLIENT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void aClientCannotCancelAnotherClientsReservation() throws Exception {
    String theirs =
        createdId(
            createReservation(OTHER_CLIENT, TABLE_ID).andExpect(status().isCreated()).andReturn());

    mockMvc
        .perform(
            post("/api/v1/reservations/" + theirs + "/cancel")
                .with(csrf())
                .with(clientLogin(CLIENT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void staffBooksAWalkInForARegisteredClientWhoThenSeesIt() throws Exception {
    String body =
        "{\"branch\":\"Silom\",\"date\":\""
            + tomorrow()
            + "\",\"startTime\":\"14:00\",\"endTime\":\"16:00\",\"partySize\":2,"
            + "\"tableId\":"
            + TABLE_ID
            + ",\"clientSubject\":\""
            + CLIENT
            + "\",\"customerName\":\"Walk In Guest\",\"phoneNumber\":\"0800000000\"}";

    String id =
        createdId(
            mockMvc
                .perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf())
                        .with(staffLogin(STAFF)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Walk In Guest"))
                .andReturn());

    // The walk-in guest sees the booking in their own history and can cancel it.
    mockMvc
        .perform(get("/api/v1/reservations").with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id));

    mockMvc
        .perform(
            post("/api/v1/reservations/" + id + "/cancel").with(csrf()).with(clientLogin(CLIENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Cancelled"));
  }

  @Test
  void clientReservationEndpointsRejectStaffAndAnonymousCallers() throws Exception {
    mockMvc
        .perform(get("/api/v1/reservations").with(staffLogin(STAFF)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/reservations")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/reservations/" + "anything" + "/cancel")
                .with(csrf())
                .with(staffLogin(STAFF)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnknownBranchIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"branch\":\"Atlantis\",\"date\":\""
                        + tomorrow()
                        + "\",\"startTime\":\"14:00\",\"endTime\":\"16:00\","
                        + "\"partySize\":2,\"tableId\":"
                        + TABLE_ID
                        + ",\"phoneNumber\":\"0123456789\"}")
                .with(csrf())
                .with(clientLogin(CLIENT)))
        .andExpect(status().isBadRequest());
  }

  private ResultActions createReservation(String subject, long tableId) throws Exception {
    return createReservation(subject, tableId, "14:00", "16:00");
  }

  private ResultActions createReservation(String subject, long tableId, String start, String end)
      throws Exception {
    String body =
        "{\"branch\":\"Silom\",\"date\":\""
            + tomorrow()
            + "\",\"startTime\":\""
            + start
            + "\",\"endTime\":\""
            + end
            + "\",\"partySize\":2,\"tableId\":"
            + tableId
            + ",\"phoneNumber\":\"0123456789\"}";
    return mockMvc.perform(
        post("/api/v1/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf())
            .with(clientLogin(subject)));
  }

  private static MockHttpServletRequestBuilder availabilityRequest() {
    return get("/api/v1/reservations/availability")
        .queryParam("branch", "Silom")
        .queryParam("date", tomorrow().toString())
        .queryParam("startTime", "14:00")
        .queryParam("endTime", "16:00")
        .queryParam("partySize", "2");
  }

  private static LocalDate tomorrow() {
    return LocalDate.now(BANGKOK).plusDays(1);
  }

  private static String createdId(MvcResult result) throws Exception {
    return new tools.jackson.databind.ObjectMapper()
        .readTree(result.getResponse().getContentAsString())
        .path("id")
        .asText();
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
        insert into store_table (id, name, branch_id, capacity, shape, status, active, last_updated)
        values (?, ?, (select id from branch where name = ?), 4, 'Round', 'Available', true, current_timestamp)
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
