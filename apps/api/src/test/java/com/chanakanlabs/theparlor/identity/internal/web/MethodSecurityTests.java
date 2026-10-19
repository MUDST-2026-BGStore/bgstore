package com.chanakanlabs.theparlor.identity.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.identity.UserProfile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MethodSecurityTests.TestConfig.class)
class MethodSecurityTests {
  @Autowired CurrentUser currentUser;

  @Test
  @WithAnonymousUser
  void rejectsDirectInvocationWithoutAuthenticatedSession() {
    assertThatThrownBy(() -> currentUser.require()).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "STAFF")
  void allowsDirectInvocationWithAuthenticatedSession() {
    assertThat(currentUser.require().subject()).isEqualTo("subject-123");
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    CurrentUser currentUser() {
      var current = spy(new CurrentUser());
      when(current.find())
          .thenReturn(
              Optional.of(
                  new UserProfile(
                      "https://issuer.example/realms/theparlor",
                      "subject-123",
                      "operator",
                      null,
                      null,
                      false,
                      List.of("staff"))));
      return current;
    }
  }
}
