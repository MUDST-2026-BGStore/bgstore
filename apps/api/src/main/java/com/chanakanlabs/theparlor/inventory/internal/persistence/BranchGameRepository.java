package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGame;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BranchGameRepository extends JpaRepository<BranchGame, BranchGameId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from BranchGame g where g.id = :id")
  Optional<BranchGame> lockById(BranchGameId id);

  List<BranchGame> findByIdBranchId(UUID branchId);

  @Query("select g from BranchGame g where g.id.branchId = :branchId order by g.id.gameId")
  List<BranchGame> firstOfBranch(UUID branchId, Limit limit);

  @Query(
      "select g from BranchGame g where g.id.branchId = :branchId and g.id.gameId > :after"
          + " order by g.id.gameId")
  List<BranchGame> nextOfBranch(UUID branchId, UUID after, Limit limit);
}
