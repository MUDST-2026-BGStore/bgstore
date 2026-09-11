package com.chanakanlabs.bgstore.tables;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Maps the persistence model to the tables module's stable repository port. */
@Repository
class JpaTableRepositoryAdapter implements TableRepository {

  private final JpaTableRepository database;

  JpaTableRepositoryAdapter(JpaTableRepository database) {
    this.database = database;
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
        .map(TableEntity::toRecord)
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
    return database.findById(id).map(TableEntity::toRecord);
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
    return database.save(entity).toRecord();
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
}
