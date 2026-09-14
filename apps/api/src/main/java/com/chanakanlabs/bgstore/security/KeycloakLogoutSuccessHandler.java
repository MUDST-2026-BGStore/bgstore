package com.chanakanlabs.bgstore.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Completes a BFF logout by ending the corresponding Keycloak SSO session as well.
 *
 * <p>The provider endpoints are configured explicitly because the BFF reaches Keycloak through a
 * private Docker/Kubernetes address while the browser needs the public address. Spring Security's
 * discovery-based OIDC logout handler cannot use that split configuration, so this small adapter
 * builds the standard RP-initiated logout request from the authenticated OIDC principal.
 */
final class KeycloakLogoutSuccessHandler implements LogoutSuccessHandler {

  private final ClientRegistrationRepository clientRegistrations;
  private final URI endSessionEndpoint;

  KeycloakLogoutSuccessHandler(
      ClientRegistrationRepository clientRegistrations, String keycloakPublicUrl) {
    this(clientRegistrations, URI.create(keycloakPublicUrl));
  }

  KeycloakLogoutSuccessHandler(
      ClientRegistrationRepository clientRegistrations, URI keycloakPublicUrl) {
    this.clientRegistrations = clientRegistrations;
    this.endSessionEndpoint =
        URI.create(
            keycloakPublicUrl.toString().replaceAll("/+$", "")
                + "/realms/bgstore/protocol/openid-connect/logout");
  }

  @Override
  public void onLogoutSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      @Nullable Authentication authentication)
      throws IOException, ServletException {
    String logoutUri = logoutUri(request, authentication);
    if ("application/json".equalsIgnoreCase(request.getHeader("Accept"))) {
      // Return the server-generated target as plain text; do not hand-roll JSON in this security
      // boundary.
      response.setContentType("text/plain");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write(logoutUri);
      return;
    }

    response.sendRedirect(logoutUri);
  }

  private String logoutUri(HttpServletRequest request, @Nullable Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken oauth2
        && oauth2.getPrincipal() instanceof OidcUser oidcUser) {
      ClientRegistration registration =
          clientRegistrations.findByRegistrationId(oauth2.getAuthorizedClientRegistrationId());
      if (registration != null) {
        String redirectUri = applicationRoot(request);
        String logoutUri =
            UriComponentsBuilder.fromUri(endSessionEndpoint)
                .queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue())
                .queryParam("client_id", registration.getClientId())
                .queryParam("post_logout_redirect_uri", redirectUri)
                .encode()
                .build()
                .toUriString();
        return logoutUri;
      }
    }

    return applicationRoot(request);
  }

  private static String applicationRoot(HttpServletRequest request) {
    return UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
        .replacePath("/")
        .replaceQuery(null)
        .fragment(null)
        .build()
        .toUriString();
  }
}
