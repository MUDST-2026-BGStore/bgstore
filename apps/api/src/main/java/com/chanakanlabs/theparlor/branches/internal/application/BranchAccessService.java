package com.chanakanlabs.theparlor.branches.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignmentId;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchRepository;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchStaffAssignmentRepository;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class BranchAccessService implements BranchAccess {
  private final BranchRepository branches;
  private final BranchStaffAssignmentRepository assignments;
  private final CurrentUser currentUser;

  public BranchAccessService(
      BranchRepository branches,
      BranchStaffAssignmentRepository assignments,
      CurrentUser currentUser) {
    this.branches = branches;
    this.assignments = assignments;
    this.currentUser = currentUser;
  }

  public void requireActive(UUID branchId) {
    if (branches.findById(branchId).filter(branch -> branch.isActive()).isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
  }

  public String requireOperator(UUID branchId) {
    var user = currentUser.require();
    if (!user.roles().contains("admin")
        && !user.roles().contains("manager")
        && !(user.roles().contains("staff")
            && assignments.existsById(new BranchStaffAssignmentId(branchId, user.subject()))))
      throw new AccessDeniedException("Branch operator access required");
    requireActive(branchId);
    return user.subject();
  }
}
