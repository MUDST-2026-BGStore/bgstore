package com.chanakanlabs.bgstore.clients;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the staff directory endpoints against a real PostgreSQL: the client directory used for
 * staff-created reservations and the manager-only staff branch-assignment administration.
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
class StaffDirectoryApiIntegrationTest {

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

  private static final String CLIENT = "directory-client";
  private static final String STAFF = "directory-staff";
  private static final String MANAGER = "directory-manager";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate database;

  @BeforeEach
  void seed() {
    for (String subject : List.of(CLIENT, STAFF, MANAGER)) {
      database.update("delete from staff_branch_assignment where staff_subject = ?", subject);
      database.update("delete from client_profiles where subject = ?", subject);
      database.update("delete from identity_accounts where subject = ?", subject);
    }

    account(CLIENT, "CLIENT");
    account(STAFF, "STAFF");
    account(MANAGER, "MANAGER");
    database.update(
        """
        insert into staff_branch_assignment (staff_subject, branch_id)
        select ?, id from branch where name = 'Silom'
        on conflict (staff_subject, branch_id) do nothing
        """,
        STAFF);
  }

  @Test
  void staffSeesRegisteredClientsInTheDirectory() throws Exception {
    mockMvc
        .perform(get("/api/v1/clients").with(login(STAFF, "STAFF", "Staff")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[?(@.subject == '" + CLIENT + "')].displayName")
                .value(org.hamcrest.Matchers.hasItem("Local Account")));
  }

  @Test
  void aClientCannotListTheClientDirectory() throws Exception {
    mockMvc
        .perform(get("/api/v1/clients").with(login(CLIENT, "CLIENT", "Client")))
        .andExpect(status().isForbidden());
  }

  @Test
  void theManagerListsStaffWithTheirAssignedBranches() throws Exception {
    mockMvc
        .perform(get("/api/v1/staff/branch-assignments").with(login(MANAGER, "MANAGER", "Manager")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[?(@.staffSubject == '" + STAFF + "')].branchIds.length()")
                .value(org.hamcrest.Matchers.hasItem(1)));
  }

  @Test
  void staffAndClientsCannotReadBranchAssignments() throws Exception {
    mockMvc
        .perform(get("/api/v1/staff/branch-assignments").with(login(STAFF, "STAFF", "Staff")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/staff/branch-assignments")).andExpect(status().isUnauthorized());
  }

  @Test
  void theManagerReplacesAStaffMembersBranchAssignments() throws Exception {
    UUID silom = branchId("Silom");
    UUID sukhumvit = branchId("Sukhumvit");

    mockMvc
        .perform(
            put("/api/v1/staff/branch-assignments/" + STAFF)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchIds\":[\"" + silom + "\",\"" + sukhumvit + "\"]}")
                .with(csrf())
                .with(login(MANAGER, "MANAGER", "Manager")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.staffSubject").value(STAFF))
        .andExpect(jsonPath("$.branchIds.length()").value(2));

    Integer stored =
        database.queryForObject(
            "select count(*) from staff_branch_assignment where staff_subject = ?",
            Integer.class,
            STAFF);
    org.assertj.core.api.Assertions.assertThat(stored).isEqualTo(2);

    // Replacing again must not accumulate rows.
    mockMvc
        .perform(
            put("/api/v1/staff/branch-assignments/" + STAFF)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchIds\":[\"" + silom + "\"]}")
                .with(csrf())
                .with(login(MANAGER, "MANAGER", "Manager")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branchIds.length()").value(1));

    stored =
        database.queryForObject(
            "select count(*) from staff_branch_assignment where staff_subject = ?",
            Integer.class,
            STAFF);
    org.assertj.core.api.Assertions.assertThat(stored).isEqualTo(1);
  }

  @Test
  void anUnknownStaffSubjectOrBranchIsRejected() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/staff/branch-assignments/ghost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchIds\":[\"" + branchId("Silom") + "\"]}")
                .with(csrf())
                .with(login(MANAGER, "MANAGER", "Manager")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/staff/branch-assignments/" + STAFF)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchIds\":[\"" + UUID.randomUUID() + "\"]}")
                .with(csrf())
                .with(login(MANAGER, "MANAGER", "Manager")))
        .andExpect(status().isBadRequest());
  }

  private UUID branchId(String name) {
    return UUID.fromString(
        database.queryForObject("select id from branch where name = ?", String.class, name));
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
