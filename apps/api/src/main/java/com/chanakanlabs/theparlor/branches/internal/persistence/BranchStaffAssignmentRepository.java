package com.chanakanlabs.theparlor.branches.internal.persistence;

import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignment;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchStaffAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchStaffAssignmentRepository
    extends JpaRepository<BranchStaffAssignment, BranchStaffAssignmentId> {}
