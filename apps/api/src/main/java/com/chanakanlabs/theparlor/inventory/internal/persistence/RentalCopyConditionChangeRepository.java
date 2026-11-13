package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopyConditionChange;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalCopyConditionChangeRepository
    extends JpaRepository<RentalCopyConditionChange, UUID> {
  /** The most recent change of each copy; copies that never changed are absent. */
  @Query(
      value =
          "SELECT DISTINCT ON (copy_id) * FROM rental_copy_condition_changes"
              + " WHERE copy_id IN (:copyIds) ORDER BY copy_id, created_at DESC, id DESC",
      nativeQuery = true)
  List<RentalCopyConditionChange> latestOf(@Param("copyIds") Collection<UUID> copyIds);
}
