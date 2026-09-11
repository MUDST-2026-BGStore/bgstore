package com.chanakanlabs.bgstore.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class SignUpAuthorizationRequestResolverTest {

  private final SignUpAuthorizationRequestResolver resolver =
      new SignUpAuthorizationRequestResolver(
          new InMemoryClientRegistrationRepository(
              ClientRegistration.withRegistrationId("keycloak")
                  .clientId("bgstore-web")
                  .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                  .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                  .scope("openid")
                  .authorizationUri("https://id.example.test/auth")
                  .tokenUri("https://id.example.test/token")
                  .build()));

  @Test
  void asksTheProviderForItsRegistrationFormWhenTheVisitorChoseSignUp() {
    var request = authorizationRequest();
    request.setParameter("signup", "");

    OAuth2AuthorizationRequest authorization = resolver.resolve(request);

    assertThat(authorization).isNotNull();
    assertThat(authorization.getAdditionalParameters()).containsEntry("prompt", "create");
    assertThat(authorization.getAuthorizationRequestUri()).contains("prompt=create");
  }

  @Test
  void leavesAnOrdinarySignInUntouched() {
    OAuth2AuthorizationRequest authorization = resolver.resolve(authorizationRequest());

    assertThat(authorization).isNotNull();
    assertThat(authorization.getAdditionalParameters()).doesNotContainKey("prompt");
  }

  @Test
  void ignoresRequestsThatAreNotAnAuthorization() {
    assertThat(resolver.resolve(new MockHttpServletRequest("GET", "/api/v1/me"))).isNull();
  }

  private static MockHttpServletRequest authorizationRequest() {
    var request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
    request.setServletPath("/oauth2/authorization/keycloak");
    return request;
  }
}
