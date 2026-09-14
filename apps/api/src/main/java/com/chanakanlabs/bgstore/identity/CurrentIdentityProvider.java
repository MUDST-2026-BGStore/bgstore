package com.chanakanlabs.bgstore.identity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentIdentityProvider {

  public AuthenticatedIdentity currentIdentity() {
    return findCurrentIdentity()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "An OIDC session is required."));
  }

  /** The signed-in identity, or empty for a guest on a public endpoint. */
  public Optional<AuthenticatedIdentity> findCurrentIdentity() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      return Optional.empty();
    }

    return Optional.of(identityOf(oidcUser));
  }

  private static AuthenticatedIdentity identityOf(OidcUser oidcUser) {
    return new AuthenticatedIdentity(
        requiredClaim(oidcUser, "sub"),
        requiredClaim(oidcUser, "preferred_username"),
        requiredClaim(oidcUser, "email"),
        stringClaim(oidcUser, "given_name"),
        stringClaim(oidcUser, "family_name"),
        applicationRoles(oidcUser),
        branchScope(oidcUser));
  }

  private static String requiredClaim(OidcUser user, String name) {
    String value = stringClaim(user, name);
    if (value.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "The identity provider did not provide the required " + name + " claim.");
    }
    return value;
  }

  private static String stringClaim(OidcUser user, String name) {
    String value = user.getClaimAsString(name);
    return value == null ? "" : value;
  }

  private static Set<ApplicationRole> applicationRoles(OidcUser user) {
    Map<String, Object> realmAccess = user.getClaimAsMap("realm_access");
    if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
      return Set.of();
    }

    var applicationRoles = EnumSet.noneOf(ApplicationRole.class);
    roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(ApplicationRole::fromClaim)
        .flatMap(Optional::stream)
        .forEach(applicationRoles::add);
    return Set.copyOf(applicationRoles);
  }

  private static Set<String> branchScope(OidcUser user) {
    var values = new HashSet<String>();
    addClaim(values, user.getClaim("branch"));
    addClaim(values, user.getClaim("branch_id"));
    addClaim(values, user.getClaim("branches"));
    return Set.copyOf(values);
  }

  private static void addClaim(Set<String> values, @Nullable Object claim) {
    if (claim instanceof String value && !value.isBlank()) values.add(value.trim());
    if (claim instanceof Collection<?> collection) {
      collection.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .map(String::trim)
          .filter(value -> !value.isBlank())
          .forEach(values::add);
    }
  }
}
