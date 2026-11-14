package com.chanakanlabs.theparlor.branches.internal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chanakanlabs.theparlor.account.AccountDirectory;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignment;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignmentId;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchRepository;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchStaffAssignmentRepository;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.identity.UserProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StaffAssignmentsServiceTests {
  final UUID branchId = UUID.randomUUID();
  final Instant now = Instant.parse("2026-11-12T05:00:00Z");
  final BranchRepository branches = mock(BranchRepository.class);
  final BranchStaffAssignmentRepository assignments = mock(BranchStaffAssignmentRepository.class);
  final AccountDirectory accounts = mock(AccountDirectory.class);
  final CurrentUser currentUser = mock(CurrentUser.class);
  final StaffAssignmentsService service =
      new StaffAssignmentsService(
          branches, assignments, accounts, currentUser, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void grantIsIdempotentAndEnrichesAccountContact() {
    when(branches.existsById(branchId)).thenReturn(true);
    when(currentUser.require())
        .thenReturn(
            new UserProfile("issuer", "admin-1", "admin", null, null, true, List.of("admin")));
    var existing =
        BranchStaffAssignment.grant(branchId, "staff-1", "admin-0", now.minusSeconds(60));
    when(assignments.findById(new BranchStaffAssignmentId(branchId, "staff-1")))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    when(assignments.saveAndFlush(any(BranchStaffAssignment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(accounts.findBySubject("staff-1"))
        .thenReturn(
            Optional.of(
                new AccountDirectory.AccountContact(
                    UUID.randomUUID(), "Krit Somchai", "+66812345678")));

    var created = service.grant(branchId, "staff-1");
    assertThat(created.getStaffSubject()).isEqualTo("staff-1");
    assertThat(created.getGrantedBy()).isEqualTo("admin-1");
    assertThat(created.getName()).isEqualTo("Krit Somchai");
    assertThat(created.getPhone()).isEqualTo("+66812345678");

    var repeated = service.grant(branchId, "staff-1");
    assertThat(repeated.getGrantedBy()).isEqualTo("admin-0");
    verify(assignments, times(1)).saveAndFlush(any());
  }

  @Test
  void revokeDeletesWhenPresentAndSucceedsWhenMissing() {
    var existing = BranchStaffAssignment.grant(branchId, "staff-1", "admin-1", now);
    when(assignments.findById(new BranchStaffAssignmentId(branchId, "staff-1")))
        .thenReturn(Optional.of(existing))
        .thenReturn(Optional.empty());

    service.revoke(branchId, "staff-1");
    verify(assignments, times(1)).delete(existing);

    service.revoke(branchId, "staff-1");
    verify(assignments, times(1)).delete(any());
  }

  @Test
  void rejectsUnknownBranchWith404AndInvalidSubjectWith400() {
    when(branches.existsById(branchId)).thenReturn(false);

    assertThatThrownBy(() -> service.list(branchId))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> service.grant(branchId, "staff-1"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    when(branches.existsById(branchId)).thenReturn(true);
    assertThatThrownBy(() -> service.grant(branchId, " "))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> service.grant(branchId, "x".repeat(256)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
