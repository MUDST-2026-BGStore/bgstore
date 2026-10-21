package com.chanakanlabs.theparlor.branches.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
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
  @EmbeddedId private BranchStaffAssignmentId id;

  @Column(name = "granted_by", nullable = false)
  private String grantedBy;

  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;
}
