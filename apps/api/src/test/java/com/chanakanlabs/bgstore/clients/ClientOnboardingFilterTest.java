package com.chanakanlabs.bgstore.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.identity.ApplicationRole;
import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClientOnboardingFilterTest {

  @Test
  void blocksClientApiRequestsUntilOnboardingIsComplete() throws Exception {
    CurrentIdentityProvider identityProvider = mock(CurrentIdentityProvider.class);
    CurrentUserService currentUsers = mock(CurrentUserService.class);
    var filter = new ClientOnboardingFilter(identityProvider, currentUsers);
    var request = new MockHttpServletRequest("GET", "/api/v1/hello");
    var response = new MockHttpServletResponse();
    var filterChain = mock(jakarta.servlet.FilterChain.class);
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity(
            "test-client",
            "client",
            "client@example.test",
            "Client",
            "One",
            Set.of(ApplicationRole.CLIENT));
    when(identityProvider.currentIdentity()).thenReturn(identity);
    when(currentUsers.requiresOnboarding(identity)).thenReturn(true);

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(428);
    assertThat(response.getContentAsString()).contains("Profile onboarding required");
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void leavesTheUserContextAndProfileEndpointsAvailable() throws Exception {
    var filter =
        new ClientOnboardingFilter(
            mock(CurrentIdentityProvider.class), mock(CurrentUserService.class));
    var request = new MockHttpServletRequest("GET", "/api/v1/me");
    var response = new MockHttpServletResponse();
    var filterChain = mock(jakarta.servlet.FilterChain.class);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }
}
