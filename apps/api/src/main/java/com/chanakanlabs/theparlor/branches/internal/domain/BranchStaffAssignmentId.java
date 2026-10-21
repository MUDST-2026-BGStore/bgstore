package com.chanakanlabs.theparlor.branches.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record BranchStaffAssignmentId(
    @Column(name = "branch_id") UUID branchId, @Column(name = "staff_subject") String staffSubject)
    implements Serializable {}
