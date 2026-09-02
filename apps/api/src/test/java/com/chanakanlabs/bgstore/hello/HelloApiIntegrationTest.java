package com.chanakanlabs.bgstore.hello;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class HelloApiIntegrationTest {

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
  void authenticatedUserCanReachApiAndDatabase() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/hello")
                .with(
                    oidcLogin()
                        .idToken(
                            token ->
                                token.claims(
                                    claims ->
                                        claims.putAll(
                                            Map.of(
                                                "sub", "hello-test-user",
                                                "preferred_username", "hello@example.test",
                                                "email", "hello@example.test",
                                                "given_name", "Hello",
                                                "family_name", "Test"))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Hello, BGStore!"))
        .andExpect(jsonPath("$.service").value("bgstore-api"))
        .andExpect(jsonPath("$.database").value("connected"));
  }

  @Test
  void anonymousUserCannotReachApi() throws Exception {
    mockMvc.perform(get("/api/v1/hello")).andExpect(status().isUnauthorized());
  }
}
