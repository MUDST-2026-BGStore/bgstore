package com.chanakanlabs.bgstore.tables;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface TableRepository {

  /**
   * A {@code search} matches part of a table name or a table id exactly. {@code activeOnly} leaves
   * out tables taken out of service.
   */
  List<TableRecordData> findAll(
      @Nullable String branch,
      @Nullable String status,
      @Nullable String search,
      boolean activeOnly);

  /** Counts of in-service tables keyed by status; statuses no table is in are left out. */
  Map<String, Long> countActiveByStatus(@Nullable String branch);

  Optional<TableRecordData> findById(Long id);

  TableRecordData save(TableRecordData table);

  boolean deleteById(Long id);

  boolean existsByNameAndBranch(String name, String branch, @Nullable Long excludeId);
}
