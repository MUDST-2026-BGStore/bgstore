package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEdition;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEditionId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BranchEditionRepository extends JpaRepository<BranchEdition, BranchEditionId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from BranchEdition e where e.id = :id")
  Optional<BranchEdition> lockById(BranchEditionId id);
}
