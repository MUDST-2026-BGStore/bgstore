package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RentalCopyRepository
    extends JpaRepository<RentalCopy, UUID>, JpaSpecificationExecutor<RentalCopy> {
  List<RentalCopy> findByBranchIdAndGameIdIn(UUID branchId, Collection<UUID> gameIds);

  /** Locks in ID order so concurrent writers on one game's copies cannot deadlock. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select c from RentalCopy c where c.branchId = :branchId and c.gameId = :gameId"
          + " order by c.id")
  List<RentalCopy> lockByBranchIdAndGameId(UUID branchId, UUID gameId);
}
