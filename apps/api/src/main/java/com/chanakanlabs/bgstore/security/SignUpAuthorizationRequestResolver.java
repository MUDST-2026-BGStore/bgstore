package com.chanakanlabs.bgstore.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Opens the identity provider's registration form, rather than its sign-in form, for a visitor who
 * chose Sign up: the app-owned authorization entrypoint carries the standard OIDC {@code
 * prompt=create}, so no provider-specific registration name leaks into the browser.
 */
final class SignUpAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  static final String SIGN_UP_PARAMETER = "signup";

  private final OAuth2AuthorizationRequestResolver delegate;

  SignUpAuthorizationRequestResolver(
      ClientRegistrationRepository registrations, String authorizationBaseUri) {
    this.delegate =
        new DefaultOAuth2AuthorizationRequestResolver(registrations, authorizationBaseUri);
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
