package com.chanakanlabs.theparlor.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** The module interface for reading a server-authenticated session identity. */
@Component
public class CurrentUser {
  public Optional<UserProfile> find() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof OidcUser principal)) return Optional.empty();
    var roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(Objects::nonNull)
            .filter(role -> role.startsWith("ROLE_"))
            .map(role -> role.substring(5).toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
    String username = principal.getPreferredUsername();
    String displayName = principal.getFullName();
    return Optional.of(
        new UserProfile(
            Objects.requireNonNull(
                    principal.getIdToken().getIssuer(),
                    "OIDC ID token is missing the required issuer claim")
                .toString(),
            principal.getSubject(),
            username != null ? username : principal.getSubject(),
            displayName,
            principal.getEmail(),
            Boolean.TRUE.equals(principal.getEmailVerified()),
            roles));
  }

  @PreAuthorize("isAuthenticated()")
  public UserProfile require() {
    return find()
        .orElseThrow(
            () -> new InsufficientAuthenticationException("An authenticated session is required"));
  }
}
