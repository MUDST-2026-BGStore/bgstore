package com.chanakanlabs.theparlor.identity.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.chanakanlabs.theparlor.identity.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class LoginLocaleTests {
  @Test
  void carriesTheSelectedLoginLanguageInTheExistingSession() {
    var request = new MockHttpServletRequest();
    var session = request.getSession();
    var controller =
        new SessionController(
            mock(CurrentUser.class), request, "http://localhost:8081/realms/theparlor");

    var response =
        controller.loginLocale(
            "th", "http://localhost:8081/realms/theparlor/login-actions/authenticate?kc_locale=th");

    assertThat(response.getStatusCode().value()).isEqualTo(302);
    assertThat(response.getHeaders().getFirst("Set-Cookie"))
        .contains("theparlor.loginLocale=th-TH", "SameSite=Lax");
  }

  @Test
  void refusesAnExternalOrMismatchedReturnUrl() {
    var request = new MockHttpServletRequest();
    request.getSession();
    var controller =
        new SessionController(
            mock(CurrentUser.class), request, "http://localhost:8081/realms/theparlor");

    assertThatThrownBy(
            () ->
                controller.loginLocale(
                    "th", "https://example.invalid/realms/theparlor/?kc_locale=th"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                controller.loginLocale(
                    "th", "http://localhost:8081/realms/theparlor/?kc_locale=en"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(request.getSession().getAttribute("theparlor.loginLocale")).isNull();
  }
}
