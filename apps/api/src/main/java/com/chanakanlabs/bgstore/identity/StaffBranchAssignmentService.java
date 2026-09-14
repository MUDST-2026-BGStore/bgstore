package com.chanakanlabs.bgstore.identity;

import com.chanakanlabs.bgstore.contract.model.ReplaceStaffBranchAssignmentsRequest;
import com.chanakanlabs.bgstore.contract.model.StaffBranchAssignment;
import com.chanakanlabs.bgstore.contract.model.StaffBranchAssignmentList;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StaffBranchAssignmentService {
  private final StaffBranchAssignmentJpaRepository assignments;
  private final IdentityAccountJpaRepository accounts;
  private final BranchAccessPort branchAccess;
  private final AccessPolicy accessPolicy;

  StaffBranchAssignmentService(
      StaffBranchAssignmentJpaRepository assignments,
      IdentityAccountJpaRepository accounts,
      BranchAccessPort branchAccess,
      AccessPolicy accessPolicy) {
    this.assignments = assignments;
    this.accounts = accounts;
    this.branchAccess = branchAccess;
    this.accessPolicy = accessPolicy;
  }

  @Transactional(readOnly = true)
  public StaffBranchAssignmentList list() {
    accessPolicy.requireManager();
    var items =
        accounts.findOperationalAccounts().stream()
            .filter(account -> "STAFF".equals(account.applicationRole()))
            .map(
                account ->
                    new StaffBranchAssignment(
                            account.subject(),
                            account.username(),
                            assignments.findBranchIds(account.subject()).stream().sorted().toList())
                        .email(account.email()))
            .toList();
    return new StaffBranchAssignmentList(items);
  }

  public StaffBranchAssignment replace(
      String subject, ReplaceStaffBranchAssignmentsRequest request) {
    accessPolicy.requireManager();
    var account =
        accounts.findById(subject).orElseThrow(() -> notFound("Staff account not found."));
    if (!"STAFF".equals(account.applicationRole()))
      throw badRequest("Only staff accounts can be assigned branches.");
    var requested = new HashSet<UUID>(request.getBranchIds());
    if (!requested.stream().allMatch(branchAccess::exists))
      throw badRequest("Every assigned branch must exist.");
    assignments.deleteByIdStaffSubject(subject);
    assignments.saveAll(
        requested.stream()
            .map(branch -> new StaffBranchAssignmentEntity(subject, branch))
            .toList());
    return new StaffBranchAssignment(
            subject, account.username(), requested.stream().sorted().toList())
        .email(account.email());
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }
}
