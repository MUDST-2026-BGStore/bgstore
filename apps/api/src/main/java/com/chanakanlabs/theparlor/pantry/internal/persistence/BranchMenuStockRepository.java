package com.chanakanlabs.theparlor.pantry.internal.persistence;

import com.chanakanlabs.theparlor.pantry.internal.domain.BranchMenuStock;
import com.chanakanlabs.theparlor.pantry.internal.domain.BranchMenuStockId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BranchMenuStockRepository
    extends JpaRepository<BranchMenuStock, BranchMenuStockId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from BranchMenuStock s where s.id = :id")
  Optional<BranchMenuStock> lockById(BranchMenuStockId id);

  List<BranchMenuStock> findByIdBranchId(UUID branchId);

  /** Starts an empty stock row unless one exists, safely under concurrent first receipts. */
  @Modifying
  @Query(
      value =
          "insert into branch_menu_stock (branch_id, menu_item_id) values (:branchId, :menuItemId)"
              + " on conflict do nothing",
      nativeQuery = true)
  void insertEmptyIfAbsent(UUID branchId, UUID menuItemId);
}
