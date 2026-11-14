package com.chanakanlabs.theparlor.branches.internal.web;

import com.chanakanlabs.theparlor.branches.internal.application.StaffAssignmentsService;
import com.chanakanlabs.theparlor.contracts.api.StaffAssignmentsApi;
import com.chanakanlabs.theparlor.contracts.model.AccountMatchList;
import com.chanakanlabs.theparlor.contracts.model.StaffAssignment;
import com.chanakanlabs.theparlor.contracts.model.StaffAssignmentList;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffAssignmentsController implements StaffAssignmentsApi {
  private final StaffAssignmentsService assignments;

  public StaffAssignmentsController(StaffAssignmentsService assignments) {
    this.assignments = assignments;
  }

  @Override
  public StaffAssignmentList listStaffAssignments(UUID branchId) {
    return assignments.list(branchId);
  }

  @Override
  public StaffAssignment grantStaffAssignment(UUID branchId, String staffSubject) {
    return assignments.grant(branchId, staffSubject);
  }

  @Override
  public void revokeStaffAssignment(UUID branchId, String staffSubject) {
    assignments.revoke(branchId, staffSubject);
  }

  @Override
  public AccountMatchList searchAccounts(String q) {
    return assignments.searchPeople(q);
  }
}
