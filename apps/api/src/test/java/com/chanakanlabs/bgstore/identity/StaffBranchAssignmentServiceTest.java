package com.chanakanlabs.bgstore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.contract.model.ReplaceStaffBranchAssignmentsRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StaffBranchAssignmentServiceTest {
  private final StaffBranchAssignmentJpaRepository assignments = mock();
  private final IdentityAccountJpaRepository accounts = mock();
  private final BranchAccessPort branches = mock();
  private final AccessPolicy accessPolicy = mock();
  private final StaffBranchAssignmentService service =
      new StaffBranchAssignmentService(assignments, accounts, branches, accessPolicy);

  private final UUID branchId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    when(accounts.findOperationalAccounts()).thenReturn(List.of());
  }

  @Test
  void listsOnlyStaffAccountsWithTheirAssignments() {
    var staff = account("staff-1", "Local Staff", "STAFF");
    var manager = account("manager-1", "Store Manager", "MANAGER");
    when(accounts.findOperationalAccounts()).thenReturn(List.of(staff, manager));
    when(assignments.findBranchIds("staff-1")).thenReturn(Set.of(branchId));

    var result = service.list();

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().getFirst().getStaffSubject()).isEqualTo("staff-1");
    assertThat(result.getItems().getFirst().getBranchIds()).containsExactly(branchId);
    verify(accessPolicy).requireManager();
  }

  @Test
  void replacesAssignmentsForAnExistingStaffAccount() {
    var staff = account("staff-1", "Local Staff", "STAFF");
    when(accounts.findById("staff-1")).thenReturn(java.util.Optional.of(staff));
    when(branches.exists(branchId)).thenReturn(true);

    var result =
        service.replace("staff-1", new ReplaceStaffBranchAssignmentsRequest(List.of(branchId)));

    assertThat(result.getBranchIds()).containsExactly(branchId);
    verify(assignments).deleteByIdStaffSubject("staff-1");
    verify(assignments).saveAll(any());
  }

  @Test
  void rejectsMissingNonStaffAndUnknownBranchAssignments() {
    when(accounts.findById("missing")).thenReturn(java.util.Optional.empty());
    assertStatus(HttpStatus.NOT_FOUND, () -> service.replace("missing", request()));

    var client = account("client-1", "Client", "CLIENT");
    when(accounts.findById("client-1")).thenReturn(java.util.Optional.of(client));
    assertStatus(HttpStatus.BAD_REQUEST, () -> service.replace("client-1", request()));

    var staff = account("staff-2", "Other Staff", "STAFF");
    when(accounts.findById("staff-2")).thenReturn(java.util.Optional.of(staff));
    when(branches.exists(branchId)).thenReturn(false);
    assertStatus(HttpStatus.BAD_REQUEST, () -> service.replace("staff-2", request()));
  }

  private ReplaceStaffBranchAssignmentsRequest request() {
    return new ReplaceStaffBranchAssignmentsRequest(List.of(branchId));
  }

  private static IdentityAccount account(String subject, String username, String role) {
    var account = mock(IdentityAccount.class);
    when(account.subject()).thenReturn(subject);
    when(account.username()).thenReturn(username);
    when(account.email())
        .thenReturn(username.toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.test");
    when(account.applicationRole()).thenReturn(role);
    return account;
  }

  private static void assertStatus(HttpStatus status, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(status));
  }
}
