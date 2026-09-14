package com.chanakanlabs.bgstore.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class KeycloakLogoutSuccessHandlerTest {

  private final ClientRegistration registration =
      ClientRegistration.withRegistrationId("bgstore")
          .clientId("bgstore-web")
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("{baseUrl}/auth/callback/{registrationId}")
          .scope("openid")
          .authorizationUri("https://id.example.test/auth")
          .tokenUri("https://id.example.test/token")
          .build();

  @Test
  void redirectsToKeycloakAndEndsTheProviderSession() throws Exception {
    var handler =
        new KeycloakLogoutSuccessHandler(
            new InMemoryClientRegistrationRepository(registration), "https://id.example.test/");
    var idToken =
        new OidcIdToken(
            "signed-id-token",
            Instant.parse("2026-09-12T00:00:00Z"),
            Instant.parse("2026-09-12T01:00:00Z"),
            Map.of("sub", "subject"));
    var principal =
        new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, "sub");
    var authentication =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "bgstore");
    var request = new MockHttpServletRequest("POST", "/logout");
    request.setScheme("https");
    request.setServerName("app.example.test");
    request.setServerPort(443);
    var response = new MockHttpServletResponse();

    handler.onLogoutSuccess(request, response, authentication);

    assertThat(response.getRedirectedUrl())
        .startsWith("https://id.example.test/realms/bgstore/protocol/openid-connect/logout?")
        .contains("id_token_hint=signed-id-token")
        .contains("client_id=bgstore-web")
        .contains("post_logout_redirect_uri=https://app.example.test/");
  }
}
