package com.chanakanlabs.theparlor.branches.internal.application;

import com.chanakanlabs.theparlor.account.AccountDirectory;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignment;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignmentId;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchRepository;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchStaffAssignmentRepository;
import com.chanakanlabs.theparlor.contracts.model.AccountMatch;
import com.chanakanlabs.theparlor.contracts.model.AccountMatchList;
import com.chanakanlabs.theparlor.contracts.model.StaffAssignment;
import com.chanakanlabs.theparlor.contracts.model.StaffAssignmentList;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Which staff accounts may operate which branch. Administrators grant and revoke; managers and
 * administrators read. Roles themselves stay in Keycloak.
 */
@Service
@Transactional(readOnly = true)
public class StaffAssignmentsService {
  static final int SEARCH_LIMIT = 20;

  private final BranchRepository branches;
  private final BranchStaffAssignmentRepository assignments;
  private final AccountDirectory accounts;
  private final CurrentUser currentUser;
  private final Clock clock;

  public StaffAssignmentsService(
      BranchRepository branches,
      BranchStaffAssignmentRepository assignments,
      AccountDirectory accounts,
      CurrentUser currentUser,
      Clock clock) {
    this.branches = branches;
    this.assignments = assignments;
    this.accounts = accounts;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /** Inactive branches are included so administrators can prepare them. */
  @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
  public StaffAssignmentList list(UUID branchId) {
    requireBranch(branchId);
    return new StaffAssignmentList(
        assignments.findForBranch(branchId).stream().map(this::toContract).toList());
  }

  /** Returns the existing assignment unchanged when it is already granted. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public StaffAssignment grant(UUID branchId, String staffSubject) {
    requireBranch(branchId);
    if (staffSubject == null
        || staffSubject.isBlank()
        || staffSubject.length() > BranchStaffAssignment.MAX_SUBJECT_LENGTH)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Staff subject must be 1 to " + BranchStaffAssignment.MAX_SUBJECT_LENGTH + " characters");
    var id = new BranchStaffAssignmentId(branchId, staffSubject);
    var assignment =
        assignments
            .findById(id)
            .orElseGet(
                () ->
                    assignments.saveAndFlush(
                        BranchStaffAssignment.grant(
                            branchId,
                            staffSubject,
                            currentUser.require().subject(),
                            clock.instant())));
    return toContract(assignment);
  }

  /** Always succeeds; revoking an assignment that does not exist changes nothing. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public void revoke(UUID branchId, String staffSubject) {
    assignments
        .findById(new BranchStaffAssignmentId(branchId, staffSubject))
        .ifPresent(assignments::delete);
  }

  @PreAuthorize("hasRole('ADMIN')")
  public AccountMatchList searchPeople(String query) {
    return new AccountMatchList(
        accounts.search(query, SEARCH_LIMIT).stream()
            .map(match -> new AccountMatch(match.subject(), match.name(), match.phoneNumber()))
            .toList());
  }

  private void requireBranch(UUID branchId) {
    if (!branches.existsById(branchId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
  }

  private StaffAssignment toContract(BranchStaffAssignment assignment) {
    var subject = assignment.getId().staffSubject();
    var result =
        new StaffAssignment(
            subject, assignment.getGrantedBy(), assignment.getGrantedAt().atOffset(ZoneOffset.UTC));
    accounts
        .findBySubject(subject)
        .ifPresent(
            contact -> {
              result.setName(contact.name());
              result.setPhone(contact.phoneNumber());
            });
    return result;
  }
}
