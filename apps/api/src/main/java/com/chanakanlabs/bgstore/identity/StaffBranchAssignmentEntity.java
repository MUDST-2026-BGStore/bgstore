package com.chanakanlabs.bgstore.identity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_branch_assignment")
class StaffBranchAssignmentEntity {
  @EmbeddedId private StaffBranchAssignmentId id;

  protected StaffBranchAssignmentEntity() {}

  StaffBranchAssignmentEntity(String subject, java.util.UUID branchId) {
    id = new StaffBranchAssignmentId(subject, branchId);
  }

  StaffBranchAssignmentId id() {
    return id;
  }
}
