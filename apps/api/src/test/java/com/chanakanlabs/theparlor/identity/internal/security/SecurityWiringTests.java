package com.chanakanlabs.theparlor.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class SecurityWiringTests {
  @Test
  void passesOnlySupportedStorefrontLocalesToTheIdentityProvider() {
    var registrations =
        new SecurityConfig()
            .clientRegistrations(
                "https://identity.example/realms/theparlor",
                "https://identity.example/realms/theparlor",
                "secret",
                "https://store.example");
    var resolver = SecurityConfig.authorizationRequests(registrations);

    for (var entry : Map.of("en-US", "en", "th-TH", "th").entrySet()) {
      var request = new MockHttpServletRequest("GET", "/api/auth/login/keycloak");
      request.setServletPath("/api/auth/login/keycloak");
      request.setParameter("ui_locales", entry.getKey());

      var authorization = resolver.resolve(request);
      assertThat(authorization.getAdditionalParameters())
          .containsEntry("ui_locales", entry.getValue());
      assertThat(authorization.getAuthorizationRequestUri())
          .contains("ui_locales=" + entry.getValue());
    }

    var unsupported = new MockHttpServletRequest("GET", "/api/auth/login/keycloak");
    unsupported.setServletPath("/api/auth/login/keycloak");
    unsupported.setParameter("ui_locales", "fr-FR");
    assertThat(resolver.resolve(unsupported).getAdditionalParameters())
        .doesNotContainKey("ui_locales");
  }

  @Test
  void mapsOnlyRealmRolesFromTheValidatedIdToken() {
    var token =
        new OidcIdToken(
            "test-id-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of(
                "sub", "staff-1", "realm_access", Map.of("roles", List.of("staff", "manager", 7))));
    var authorities =
        SecurityConfig.realmAuthorities()
            .mapAuthorities(
                List.of(
                    new OidcUserAuthority(
                        token,
                        new org.springframework.security.oauth2.core.oidc.OidcUserInfo(
                            Map.of(
                                "sub",
                                "staff-1",
                                "realm_access",
                                Map.of("roles", List.of("admin")))))));
    assertThat(authorities)
        .extracting(GrantedAuthority::getAuthority)
        .contains("ROLE_STAFF", "ROLE_MANAGER")
        .doesNotContain("ROLE_7", "ROLE_ADMIN");
  }

  @Test
  void ignoresMissingAndMalformedRoleClaims() {
    for (Map<String, Object> claims :
        List.of(
            Map.<String, Object>of("sub", "staff-1"),
            Map.<String, Object>of("sub", "staff-1", "realm_access", Map.of("roles", "admin")))) {
      var token =
          new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(60), claims);
      var authorities =
          SecurityConfig.realmAuthorities().mapAuthorities(List.of(new OidcUserAuthority(token)));
      assertThat(authorities)
          .extracting(GrantedAuthority::getAuthority)
          .noneMatch(role -> role.startsWith("ROLE_"));
    }
  }
}
