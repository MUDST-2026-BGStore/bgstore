package com.chanakanlabs.bgstore.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** App-owned authentication entrypoints that keep the identity provider behind the BFF boundary. */
@RestController
class AuthController {

  private static final String REGISTRATION_ID = "bgstore";

  @GetMapping("/auth/sign-in")
  void signIn(HttpServletResponse response) throws IOException {
    redirect(response, authorizationPath());
  }

  @GetMapping("/auth/sign-up")
  void signUp(HttpServletResponse response) throws IOException {
    redirect(response, authorizationPath() + "?signup");
  }

  private static String authorizationPath() {
    return SecurityConfiguration.AUTHORIZATION_BASE_URI + "/" + REGISTRATION_ID;
  }

  private static void redirect(HttpServletResponse response, String location) {
    response.setStatus(HttpServletResponse.SC_FOUND);
    response.setHeader("Location", location);
  }
}
