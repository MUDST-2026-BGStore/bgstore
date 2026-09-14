package com.chanakanlabs.bgstore.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StaffBranchAssignmentJpaRepository
    extends JpaRepository<StaffBranchAssignmentEntity, StaffBranchAssignmentId> {
  boolean existsByIdStaffSubjectAndIdBranchId(String staffSubject, UUID branchId);

  void deleteByIdStaffSubject(String staffSubject);

  @Query(
      "select a.id.branchId from StaffBranchAssignmentEntity a where a.id.staffSubject = :subject")
  java.util.Set<UUID> findBranchIds(@Param("subject") String subject);
}
