package com.chanakanlabs.bgstore.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Opens the identity provider's registration form, rather than its sign-in form, for a visitor who
 * chose Sign up: {@code /oauth2/authorization/keycloak?signup} carries the standard OIDC {@code
 * prompt=create}, so no provider-specific URL leaks into the browser.
 */
final class SignUpAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  static final String SIGN_UP_PARAMETER = "signup";

  private final OAuth2AuthorizationRequestResolver delegate;

  SignUpAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
    this.delegate =
        new DefaultOAuth2AuthorizationRequestResolver(
            registrations,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
  }

  @Override
  public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return promptingSignUp(request, delegate.resolve(request));
  }

  @Override
  public @Nullable OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    return promptingSignUp(request, delegate.resolve(request, clientRegistrationId));
  }

  private static @Nullable OAuth2AuthorizationRequest promptingSignUp(
      HttpServletRequest request, @Nullable OAuth2AuthorizationRequest authorization) {
    if (authorization == null || request.getParameter(SIGN_UP_PARAMETER) == null) {
      return authorization;
    }

    return OAuth2AuthorizationRequest.from(authorization)
        .additionalParameters(parameters -> parameters.put("prompt", "create"))
        .build();
  }
}
