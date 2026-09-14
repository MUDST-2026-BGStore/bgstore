package com.chanakanlabs.bgstore.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class StaffBranchAssignmentId implements Serializable {
  @Column(name = "staff_subject", nullable = false)
  private String staffSubject;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  protected StaffBranchAssignmentId() {}

  StaffBranchAssignmentId(String staffSubject, UUID branchId) {
    this.staffSubject = staffSubject;
    this.branchId = branchId;
  }

  public String getStaffSubject() {
    return staffSubject;
  }

  public UUID getBranchId() {
    return branchId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof StaffBranchAssignmentId id
        && staffSubject.equals(id.staffSubject)
        && branchId.equals(id.branchId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(staffSubject, branchId);
  }
}
