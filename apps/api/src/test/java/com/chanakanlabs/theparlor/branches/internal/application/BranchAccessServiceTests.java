package com.chanakanlabs.theparlor.branches.internal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chanakanlabs.theparlor.branches.internal.domain.*;
import com.chanakanlabs.theparlor.branches.internal.persistence.*;
import com.chanakanlabs.theparlor.identity.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class BranchAccessServiceTests {
  @Test
  void staffMustHaveAnAssignmentForTheRequestedBranch() {
    var branches = mock(BranchRepository.class);
    var assignments = mock(BranchStaffAssignmentRepository.class);
    var current = mock(CurrentUser.class);
    var service = new BranchAccessService(branches, assignments, current);
    var branchId = UUID.randomUUID();
    var branch = mock(Branch.class);
    when(branch.isActive()).thenReturn(true);
    when(branches.findById(branchId)).thenReturn(Optional.of(branch));
    when(current.require())
        .thenReturn(
            new UserProfile("issuer", "staff-1", "staff", null, null, true, List.of("staff")));
    assertThatThrownBy(() -> service.requireOperator(branchId))
        .isInstanceOf(AccessDeniedException.class);
    when(assignments.existsById(new BranchStaffAssignmentId(branchId, "staff-1"))).thenReturn(true);
    assertThat(service.requireOperator(branchId)).isEqualTo("staff-1");
    when(current.require())
        .thenReturn(
            new UserProfile(
                "issuer", "manager-1", "manager", null, null, true, List.of("manager")));
    assertThat(service.requireOperator(branchId)).isEqualTo("manager-1");
  }
}
