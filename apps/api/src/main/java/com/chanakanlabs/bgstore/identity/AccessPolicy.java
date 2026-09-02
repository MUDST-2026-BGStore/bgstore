package com.chanakanlabs.bgstore.identity;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The application role policies domain services use to protect staff and manager actions.
 *
 * <p>Controllers remain thin and browser navigation is only presentational. A module invokes the
 * relevant method at the start of its command method, so the policy remains effective for every
 * transport that reaches the same domain behavior.
 */
@Component
public class AccessPolicy {

  private static final Set<ApplicationRole> STAFF_OR_MANAGER =
      Set.of(ApplicationRole.STAFF, ApplicationRole.MANAGER);

  private final CurrentIdentityProvider identities;

  public AccessPolicy(CurrentIdentityProvider identities) {
    this.identities = identities;
  }

  /** Requires an operational role for staff-facing actions such as inventory management. */
  public AuthenticatedIdentity requireStaffOrManager() {
    return requireOneOf(STAFF_OR_MANAGER, "A staff or manager role is required.");
  }

  /** Requires the administrative role for policy and staff-permission changes. */
  public AuthenticatedIdentity requireManager() {
    return requireOneOf(Set.of(ApplicationRole.MANAGER), "A manager role is required.");
  }

  private AuthenticatedIdentity requireOneOf(
      Set<ApplicationRole> permittedRoles, String forbiddenDetail) {
    AuthenticatedIdentity identity = identities.currentIdentity();
    if (identity.roles().stream().noneMatch(permittedRoles::contains)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenDetail);
    }
    return identity;
  }
}
