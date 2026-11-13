package com.chanakanlabs.theparlor.branches.internal.persistence;

import com.chanakanlabs.theparlor.branches.internal.domain.Branch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BranchRepository
    extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Branch b where b.id = :id")
  Optional<Branch> lockById(UUID id);

  Optional<Branch> findByFulfillsDeliveryTrueAndActiveTrue();
}
