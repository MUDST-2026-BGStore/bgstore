package com.chanakanlabs.theparlor.branches.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "branch_staff_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchStaffAssignment {
  /** Storage length of staff_subject and granted_by. */
  public static final int MAX_SUBJECT_LENGTH = 255;

  @EmbeddedId private BranchStaffAssignmentId id;

  @Column(name = "granted_by", nullable = false)
  private String grantedBy;

  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  /** An administrator lets a staff account operate one branch. */
  public static BranchStaffAssignment grant(
      UUID branchId, String staffSubject, String grantedBy, Instant grantedAt) {
    if (staffSubject == null
        || staffSubject.isBlank()
        || staffSubject.length() > MAX_SUBJECT_LENGTH)
      throw new IllegalArgumentException(
          "Staff subject must be 1 to " + MAX_SUBJECT_LENGTH + " characters");
    if (grantedBy == null || grantedBy.isBlank())
      throw new IllegalArgumentException("Assignments need a granting administrator");
    var assignment = new BranchStaffAssignment();
    assignment.id = new BranchStaffAssignmentId(Objects.requireNonNull(branchId), staffSubject);
    assignment.grantedBy = grantedBy;
    assignment.grantedAt = Objects.requireNonNull(grantedAt);
    return assignment;
  }
}
