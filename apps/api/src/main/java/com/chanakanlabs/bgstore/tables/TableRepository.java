package com.chanakanlabs.bgstore.tables;

import java.util.List;
import java.util.Optional;
import org.springframework.lang.Nullable;

public interface TableRepository {

  List<TableRecordData> findAll(
      @Nullable String branch,
      @Nullable String zone,
      @Nullable String status,
      @Nullable String search);

  Optional<TableRecordData> findById(Long id);

  TableRecordData save(TableRecordData table);

  boolean deleteById(Long id);

  boolean existsByNameAndBranch(String name, String branch, @Nullable Long excludeId);
}
