package com.chanakanlabs.bgstore.tables;

import com.chanakanlabs.bgstore.branches.BranchDirectory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Maps the persistence model to the tables module's stable repository port. */
@Repository
class JpaTableRepositoryAdapter implements TableRepository {

  private final JpaTableRepository database;
  private final BranchDirectory branches;

  JpaTableRepositoryAdapter(JpaTableRepository database, BranchDirectory branches) {
    this.database = database;
    this.branches = branches;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TableRecordData> findAll(
      @Nullable String branch,
      @Nullable String zone,
      @Nullable String status,
      @Nullable String search,
      boolean activeOnly) {
    return database.findFiltered(branch, zone, status, search, activeOnly).stream()
        .map(this::toRecord)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Long> countActiveByStatus(@Nullable String branch) {
    return database.countActiveByStatus(branch).stream()
        .collect(
            Collectors.toMap(
                JpaTableRepository.StatusCount::getStatus,
                JpaTableRepository.StatusCount::getTables));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TableRecordData> findById(Long id) {
    return database.findById(id).map(this::toRecord);
  }

  @Override
  @Transactional
  public TableRecordData save(TableRecordData data) {
    TableEntity entity =
        data.id() == null
            ? new TableEntity(null)
            : database
                .findById(data.id())
                .orElseThrow(() -> new IllegalStateException("Table disappeared during update"));
    entity.apply(data);
    return toRecord(database.save(entity));
  }

  @Override
  @Transactional
  public boolean deleteById(Long id) {
    if (!database.existsById(id)) {
      return false;
    }
    database.deleteById(id);
    return true;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByNameAndBranch(String name, String branch, @Nullable Long excludeId) {
    return database.existsByNameAndBranch(name, branch, excludeId);
  }

  private TableRecordData toRecord(TableEntity entity) {
    return branches
        .findById(entity.branchId())
        .map(branch -> entity.toRecord(branch.name()))
        .orElseThrow(() -> new IllegalStateException("Table references an unknown branch"));
  }
}
