package com.chanakanlabs.bgstore.clients;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "management.logging.export.otlp.enabled=false",
      "management.otlp.metrics.export.enabled=false",
      "management.tracing.export.enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
class CurrentUserApiIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.1-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.4-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "test-password");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "test-password");
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void provisionsAnIncompleteClientProfileOnTheFirstAuthenticatedRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/me").with(clientLogin("a9c7022e-a678-4d50-aa1b-69c917001234")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("a9c7022e-a678-4d50-aa1b-69c917001234"))
        .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
        .andExpect(jsonPath("$.clientProfile.completed").value(false))
        .andExpect(jsonPath("$.onboardingRequired").value(true));
  }

  @Test
  void completesTheProfileWithAThaiMobileNumber() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/me/client-profile")
                .with(clientLogin("9891d60a-7417-4cdd-b817-f40c1aa01234"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"081 234 5678\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("+66812345678"))
        .andExpect(jsonPath("$.completed").value(true));

    mockMvc
        .perform(get("/api/v1/me").with(clientLogin("9891d60a-7417-4cdd-b817-f40c1aa01234")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientProfile.phone").value("+66812345678"))
        .andExpect(jsonPath("$.onboardingRequired").value(false));
  }

  @Test
  void staffDoNotReceiveTheClientOnboardingGate() throws Exception {
    mockMvc
        .perform(get("/api/v1/me").with(staffLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("STAFF"))
        .andExpect(jsonPath("$.clientProfile").doesNotExist())
        .andExpect(jsonPath("$.onboardingRequired").value(false));
  }

  private static OidcLoginRequestPostProcessor clientLogin(String subject) {
    return oidcLogin()
        .idToken(token -> token.claims(claims -> claims.putAll(clientClaims(subject))));
  }

  private static OidcLoginRequestPostProcessor staffLogin() {
    return oidcLogin()
        .idToken(
            token ->
                token.claims(
                    claims ->
                        claims.putAll(
                            Map.of(
                                "sub", "18b1cd30-1b94-42ff-9c98-f3d709001234",
                                "preferred_username", "staff@example.test",
                                "email", "staff@example.test",
                                "given_name", "Local",
                                "family_name", "Staff",
                                "realm_access", Map.of("roles", List.of("STAFF"))))));
  }

  private static Map<String, Object> clientClaims(String subject) {
    return Map.of(
        "sub", subject,
        "preferred_username", "client@example.test",
        "email", "client@example.test",
        "given_name", "Local",
        "family_name", "Client",
        "realm_access", Map.of("roles", List.of("CLIENT")));
  }
}
