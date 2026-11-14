package com.chanakanlabs.theparlor.branches.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chanakanlabs.theparlor.account.AccountDirectory;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchRepository;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchStaffAssignmentRepository;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.identity.UserProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(StaffAssignmentsSecurityTests.TestConfig.class)
class StaffAssignmentsSecurityTests {
  private static final UUID BRANCH_ID = UUID.randomUUID();

  @Autowired StaffAssignmentsService service;

  @Test
  @WithMockUser(roles = "STAFF")
  void staffCannotListGrantRevokeOrSearch() {
    assertThatThrownBy(() -> service.list(BRANCH_ID)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.grant(BRANCH_ID, "s"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.revoke(BRANCH_ID, "s"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.searchPeople("ab")).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "MANAGER")
  void managerCanListButCannotGrantRevokeOrSearch() {
    assertThat(service.list(BRANCH_ID).getItems()).isEmpty();
    assertThatThrownBy(() -> service.grant(BRANCH_ID, "s"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.revoke(BRANCH_ID, "s"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.searchPeople("ab")).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void adminCanListGrantRevokeAndSearch() {
    assertThat(service.list(BRANCH_ID).getItems()).isEmpty();
    assertThat(service.grant(BRANCH_ID, "s").getStaffSubject()).isEqualTo("s");
    service.revoke(BRANCH_ID, "s");
    assertThat(service.searchPeople("ab").getItems()).isEmpty();
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    StaffAssignmentsService staffAssignmentsService() {
      var branches = mock(BranchRepository.class);
      when(branches.existsById(BRANCH_ID)).thenReturn(true);
      var assignments = mock(BranchStaffAssignmentRepository.class);
      when(assignments.findForBranch(BRANCH_ID)).thenReturn(List.of());
      when(assignments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
      var accounts = mock(AccountDirectory.class);
      when(accounts.search("ab", 20)).thenReturn(List.of());
      var currentUser = mock(CurrentUser.class);
      when(currentUser.require())
          .thenReturn(new UserProfile("issuer", "admin-1", "admin", null, null, true, List.of()));
      return new StaffAssignmentsService(
          branches,
          assignments,
          accounts,
          currentUser,
          Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), ZoneOffset.UTC));
    }
  }
}
