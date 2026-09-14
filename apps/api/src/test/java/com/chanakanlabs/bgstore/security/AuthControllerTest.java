package com.chanakanlabs.bgstore.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

  private final AuthController controller = new AuthController();

  @Test
  void startsSignInThroughTheAppOwnedAuthorizationPath() throws Exception {
    var response = new MockHttpServletResponse();

    controller.signIn(response);

    assertThat(response.getRedirectedUrl()).isEqualTo("/auth/provider/bgstore");
  }

  @Test
  void startsSignUpThroughTheAppOwnedAuthorizationPath() throws Exception {
    var response = new MockHttpServletResponse();

    controller.signUp(response);

    assertThat(response.getRedirectedUrl()).isEqualTo("/auth/provider/bgstore?signup");
  }
}
