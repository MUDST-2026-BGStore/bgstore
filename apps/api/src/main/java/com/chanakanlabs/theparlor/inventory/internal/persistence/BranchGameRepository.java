package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGame;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BranchGameRepository extends JpaRepository<BranchGame, BranchGameId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from BranchGame g where g.id = :id")
  Optional<BranchGame> lockById(BranchGameId id);
}
