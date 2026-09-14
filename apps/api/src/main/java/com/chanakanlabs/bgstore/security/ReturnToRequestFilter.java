package com.chanakanlabs.bgstore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

final class ReturnToRequestFilter extends OncePerRequestFilter {

  static final String SESSION_ATTRIBUTE = ReturnToRequestFilter.class.getName() + ".returnTo";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (isAuthenticationStart(request.getRequestURI())) {
      String returnTo = request.getParameter("returnTo");
      if (isSafeRelativePath(returnTo)) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, returnTo);
      }
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isAuthenticationStart(String requestUri) {
    return "/auth/sign-in".equals(requestUri) || "/auth/sign-up".equals(requestUri);
  }

  static boolean isSafeRelativePath(String value) {
    return value != null
        && value.startsWith("/")
        && !value.startsWith("//")
        && !value.contains("\\")
        && !value.contains("://")
        && value.chars().noneMatch(Character::isISOControl);
  }
}
