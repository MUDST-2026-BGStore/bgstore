package com.chanakanlabs.theparlor.branches.internal.persistence;

import com.chanakanlabs.theparlor.branches.internal.domain.BranchTable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BranchTableRepository
    extends JpaRepository<BranchTable, UUID>, JpaSpecificationExecutor<BranchTable> {
  Optional<BranchTable> findByIdAndBranchId(UUID id, UUID branchId);

  java.util.List<BranchTable> findByBranchIdOrderById(UUID branchId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from BranchTable t where t.id = :id and t.branchId = :branchId")
  Optional<BranchTable> lockByIdAndBranchId(UUID id, UUID branchId);
}
