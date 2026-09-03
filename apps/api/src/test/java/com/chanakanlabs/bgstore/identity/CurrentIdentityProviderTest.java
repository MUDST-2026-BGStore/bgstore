package com.chanakanlabs.bgstore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class CurrentIdentityProviderTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void translatesOnlyKnownKeycloakRolesAtTheSecurityBoundary() {
    OidcUser oidcUser = Mockito.mock(OidcUser.class);
    when(oidcUser.getClaimAsString("sub")).thenReturn("a9c7022e-a678-4d50-aa1b-69c917001234");
    when(oidcUser.getClaimAsString("preferred_username")).thenReturn("client@example.test");
    when(oidcUser.getClaimAsString("email")).thenReturn("client@example.test");
    when(oidcUser.getClaimAsString("given_name")).thenReturn("Local");
    when(oidcUser.getClaimAsString("family_name")).thenReturn("Client");
    when(oidcUser.getClaimAsMap("realm_access"))
        .thenReturn(Map.of("roles", List.of("CLIENT", "unrecognized-role")));
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(oidcUser, null));

    AuthenticatedIdentity identity = new CurrentIdentityProvider().currentIdentity();

    assertThat(identity.subject()).isEqualTo("a9c7022e-a678-4d50-aa1b-69c917001234");
    assertThat(identity.username()).isEqualTo("client@example.test");
    assertThat(identity.roles()).containsExactly(ApplicationRole.CLIENT);
    assertThat(identity.isClientOnly()).isTrue();
  }
}
