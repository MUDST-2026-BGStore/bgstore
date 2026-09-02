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
    if ("/oauth2/authorization/keycloak".equals(request.getRequestURI())) {
      String returnTo = request.getParameter("returnTo");
      if (isSafeRelativePath(returnTo)) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, returnTo);
      }
    }
    filterChain.doFilter(request, response);
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
