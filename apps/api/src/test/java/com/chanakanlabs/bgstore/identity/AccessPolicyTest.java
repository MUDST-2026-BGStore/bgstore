package com.chanakanlabs.bgstore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AccessPolicyTest {

  private final CurrentIdentityProvider identities = Mockito.mock(CurrentIdentityProvider.class);
  private final StaffBranchAssignmentJpaRepository assignments =
      Mockito.mock(StaffBranchAssignmentJpaRepository.class);
  private final AuthenticatedIdentity client = identityWith(ApplicationRole.CLIENT);
  private AccessPolicy policy;

  @BeforeEach
  void setUp() {
    policy = new AccessPolicy(identities, assignments);
  }

  @Test
  void deniesAClientTheStaffAndManagerPolicy() {
    when(identities.currentIdentity()).thenReturn(client);

    assertForbidden(() -> policy.requireStaffOrManager(), "staff or manager");
    assertForbidden(policy::requireManager, "manager");
  }

  @Test
  void deniesStaffAccessToClientOnlyReservationPolicy() {
    when(identities.currentIdentity()).thenReturn(identityWith(ApplicationRole.STAFF));

    assertForbidden(policy::requireClientOnly, "client");
  }

  @Test
  void allowsOnlyAClientToUseClientOnlyReservationPolicy() {
    when(identities.currentIdentity()).thenReturn(client);

    assertThat(policy.requireClientOnly()).isSameAs(client);
  }

  @Test
  void allowsStaffToPerformOperationalActionsButNotManagerOnlyActions() {
    AuthenticatedIdentity staff = identityWith(ApplicationRole.STAFF);
    when(identities.currentIdentity()).thenReturn(staff);

    assertThat(policy.requireStaffOrManager()).isSameAs(staff);
    assertForbidden(policy::requireManager, "manager");
  }

  @Test
  void allowsManagersForBothPolicies() {
    AuthenticatedIdentity manager = identityWith(ApplicationRole.MANAGER);
    when(identities.currentIdentity()).thenReturn(manager);

    assertThat(policy.requireStaffOrManager()).isSameAs(manager);
    assertThat(policy.requireManager()).isSameAs(manager);
  }

  @Test
  void allowsStaffOnlyTheirAssignedBranch() {
    var branchId = UUID.randomUUID();
    var staff = identityWith(ApplicationRole.STAFF);
    policy = new AccessPolicy(identities, assignments);
    when(identities.currentIdentity()).thenReturn(staff);
    when(identities.findCurrentIdentity()).thenReturn(java.util.Optional.of(staff));
    when(assignments.existsByIdStaffSubjectAndIdBranchId("subject", branchId)).thenReturn(true);
    when(assignments.findBranchIds("subject")).thenReturn(Set.of(branchId));

    assertThat(policy.canAccessBranch(branchId)).isTrue();
    policy.requireAnyAssignedBranch();
  }

  @Test
  void deniesStaffWhenTheBranchIsNotAssigned() {
    var branchId = UUID.randomUUID();
    policy = new AccessPolicy(identities, assignments);
    var staff = identityWith(ApplicationRole.STAFF);
    when(identities.currentIdentity()).thenReturn(staff);
    when(identities.findCurrentIdentity()).thenReturn(java.util.Optional.of(staff));
    when(assignments.existsByIdStaffSubjectAndIdBranchId("subject", branchId)).thenReturn(false);

    assertThat(policy.canAccessBranch(branchId)).isFalse();
    assertForbidden(() -> policy.requireBranch(branchId), "outside");
    when(assignments.findBranchIds("subject")).thenReturn(Set.of());
    assertForbidden(policy::requireAnyAssignedBranch, "no branch");
  }

  private static AuthenticatedIdentity identityWith(ApplicationRole role) {
    return identityWith(role, Set.of());
  }

  private static AuthenticatedIdentity identityWith(ApplicationRole role, Set<String> branchScope) {
    return new AuthenticatedIdentity(
        "subject",
        "user@example.test",
        "user@example.test",
        "Local",
        "User",
        Set.of(role),
        branchScope);
  }

  private static void assertForbidden(ThrowingCallable operation, String detailFragment) {
    assertThatThrownBy(operation::call)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> {
              assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(failure.getReason()).containsIgnoringCase(detailFragment);
            });
  }

  @FunctionalInterface
  private interface ThrowingCallable {
    void call();
  }
}
