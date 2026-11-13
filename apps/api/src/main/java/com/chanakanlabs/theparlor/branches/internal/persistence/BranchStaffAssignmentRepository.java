package com.chanakanlabs.theparlor.branches.internal.persistence;

import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignment;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignmentId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchStaffAssignmentRepository
    extends JpaRepository<BranchStaffAssignment, BranchStaffAssignmentId> {
  @Query(
      "select a from BranchStaffAssignment a where a.id.branchId = :branchId"
          + " order by a.grantedAt, a.id.staffSubject")
  List<BranchStaffAssignment> findForBranch(@Param("branchId") UUID branchId);
}
