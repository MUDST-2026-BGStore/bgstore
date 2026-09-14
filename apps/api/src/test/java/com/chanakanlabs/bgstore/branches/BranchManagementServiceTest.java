package com.chanakanlabs.bgstore.branches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BranchManagementServiceTest {

  @Mock private BranchJpaRepository branches;
  @Mock private AccessPolicy accessPolicy;

  private BranchManagementService service;

  @BeforeEach
  void setUp() {
    service = new BranchManagementService(branches, accessPolicy);
  }

  @Test
  void requiresManagerAndPersistsTrimmedBranchDetails() {
    when(branches.existsByNameIgnoreCase("New branch")).thenReturn(false);
    when(branches.save(any(BranchRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Branch created =
        service.createBranch(
            "  New branch ", "  Sukhumvit  ", LocalTime.of(9, 0), LocalTime.of(19, 0));

    verify(accessPolicy).requireManager();
    assertThat(created.name()).isEqualTo("New branch");
    assertThat(created.address()).isEqualTo("Sukhumvit");
    assertThat(created.opensAt()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void rejectsDuplicateNamesCaseInsensitively() {
    when(branches.existsByNameIgnoreCase("Central")).thenReturn(true);

    assertThatThrownBy(() -> service.createBranch("Central", null, null, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void rejectsStaffBeforeReadingOrWritingBranchData() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "A manager role is required."))
        .when(accessPolicy)
        .requireManager();

    assertThatThrownBy(() -> service.createBranch("Staff branch", null, null, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("manager role is required");
    verifyNoInteractions(branches);
  }

  @Test
  void rejectsPartialOrReversedHours() {
    assertThatThrownBy(() -> service.createBranch("Late night", null, LocalTime.of(9, 0), null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("provided together");
    assertThatThrownBy(
            () -> service.createBranch("Late night", null, LocalTime.of(19, 0), LocalTime.of(9, 0)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("before");
  }
}
