package com.chanakanlabs.bgstore.clients;

import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class ClientOnboardingFilter extends OncePerRequestFilter {

  private static final String CURRENT_USER_PATH = "/api/v1/me";
  private static final String CLIENT_PROFILE_PATH = "/api/v1/me/client-profile";

  private final CurrentIdentityProvider currentIdentityProvider;
  private final CurrentUserService currentUsers;

  public ClientOnboardingFilter(
      CurrentIdentityProvider currentIdentityProvider, CurrentUserService currentUsers) {
    this.currentIdentityProvider = currentIdentityProvider;
    this.currentUsers = currentUsers;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/")
        || path.equals(CURRENT_USER_PATH)
        || path.equals(CLIENT_PROFILE_PATH);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!currentUsers.requiresOnboarding(currentIdentityProvider.currentIdentity())) {
      filterChain.doFilter(request, response);
      return;
    }

    response.setStatus(HttpStatus.PRECONDITION_REQUIRED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            """
            {"type":"about:blank","title":"Profile onboarding required","status":428,
            "detail":"Complete your client profile before using BGStore."}
            """);
  }
}
