package com.chanakanlabs.bgstore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AccessPolicyTest {

  private final CurrentIdentityProvider identities = Mockito.mock(CurrentIdentityProvider.class);
  private final AuthenticatedIdentity client = identityWith(ApplicationRole.CLIENT);
  private AccessPolicy policy;

  @BeforeEach
  void setUp() {
    policy = new AccessPolicy(identities);
  }

  @Test
  void deniesAClientTheStaffAndManagerPolicy() {
    when(identities.currentIdentity()).thenReturn(client);

    assertForbidden(() -> policy.requireStaffOrManager(), "staff or manager");
    assertForbidden(policy::requireManager, "manager");
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

  private static AuthenticatedIdentity identityWith(ApplicationRole role) {
    return new AuthenticatedIdentity(
        "subject", "user@example.test", "user@example.test", "Local", "User", Set.of(role));
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
