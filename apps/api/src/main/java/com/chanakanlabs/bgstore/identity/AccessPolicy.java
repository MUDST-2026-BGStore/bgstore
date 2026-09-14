package com.chanakanlabs.bgstore.identity;

import java.util.Set;
import java.util.UUID;
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
  private final StaffBranchAssignmentJpaRepository assignments;

  public AccessPolicy(
      CurrentIdentityProvider identities, StaffBranchAssignmentJpaRepository assignments) {
    this.identities = identities;
    this.assignments = assignments;
  }

  /** Requires an operational role for staff-facing actions such as inventory management. */
  public AuthenticatedIdentity requireStaffOrManager() {
    return requireOneOf(STAFF_OR_MANAGER, "A staff or manager role is required.");
  }

  /** Restricts a staff identity to its assigned branch; managers are unrestricted. */
  public void requireBranch(UUID branchId) {
    var identity = requireStaffOrManager();
    if (identity.roles().contains(ApplicationRole.MANAGER)) return;
    if (!assignments.existsByIdStaffSubjectAndIdBranchId(identity.subject(), branchId))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "This branch is outside your permitted scope.");
  }

  /** Public directory reads remain public; only authenticated staff are scope-filtered. */
  public boolean canAccessBranch(UUID branchId) {
    var identity = identities.findCurrentIdentity();
    if (identity.isEmpty() || identity.get().isClientOnly()) return true;
    if (identity.get().roles().contains(ApplicationRole.MANAGER)) return true;
    return identity.get().roles().stream().anyMatch(STAFF_OR_MANAGER::contains)
        && assignments.existsByIdStaffSubjectAndIdBranchId(identity.get().subject(), branchId);
  }

  /** Requires the administrative role for policy and staff-permission changes. */
  public AuthenticatedIdentity requireManager() {
    return requireOneOf(Set.of(ApplicationRole.MANAGER), "A manager role is required.");
  }

  public boolean hasStaffAccess() {
    return identities.currentIdentity().roles().stream().anyMatch(STAFF_OR_MANAGER::contains);
  }

  public boolean hasManagerAccess() {
    return identities.currentIdentity().roles().contains(ApplicationRole.MANAGER);
  }

  /** Fails closed when an operational user has not been assigned any branch. */
  public void requireAnyAssignedBranch() {
    var identity = requireStaffOrManager();
    if (identity.roles().contains(ApplicationRole.MANAGER)) return;
    if (!assignments.findBranchIds(identity.subject()).isEmpty()) return;
    throw new ResponseStatusException(
        HttpStatus.FORBIDDEN, "This staff member has no branch assignment.");
  }

  /** Requires a client-only identity for personal reservation data and actions. */
  public AuthenticatedIdentity requireClientOnly() {
    AuthenticatedIdentity identity = identities.currentIdentity();
    if (!identity.isClientOnly()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "A client role without staff access is required.");
    }
    return identity;
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
