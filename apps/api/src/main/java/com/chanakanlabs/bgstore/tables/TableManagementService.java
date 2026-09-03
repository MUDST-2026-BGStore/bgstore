package com.chanakanlabs.bgstore.tables;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TableManagementService {

  public record PageResult<T>(List<T> items, int total, int page, int pageSize, int totalPages) {}

  private final TableRepository repository;
  private final AccessPolicy accessPolicy;

  public TableManagementService(TableRepository repository, AccessPolicy accessPolicy) {
    this.repository = repository;
    this.accessPolicy = accessPolicy;
  }

  public PageResult<TableRecordData> listTables(
      @Nullable String branch,
      @Nullable String zone,
      @Nullable String status,
      @Nullable String search,
      int page,
      int pageSize) {
    accessPolicy.requireStaffOrManager();

    List<TableRecordData> all = repository.findAll(branch, zone, status, search);
    int total = all.size();
    int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
    int safePage = Math.max(1, Math.min(page, totalPages));
    int fromIndex = Math.min((safePage - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);

    List<TableRecordData> items = all.subList(fromIndex, toIndex);
    return new PageResult<>(items, total, safePage, pageSize, totalPages);
  }

  public TableRecordData getTable(Long tableId) {
    accessPolicy.requireStaffOrManager();

    return repository
        .findById(tableId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Table not found with id: " + tableId));
  }

  public TableRecordData createTable(
      String name,
      String branch,
      int capacity,
      String shape,
      String status,
      boolean active,
      String zone) {
    accessPolicy.requireStaffOrManager();

    validateTableFields(name, branch, capacity);

    if (repository.existsByNameAndBranch(name, branch, null)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Table '" + name.trim() + "' already exists in branch '" + branch.trim() + "'.");
    }

    TableRecordData newTable =
        new TableRecordData(
            null,
            name.trim(),
            branch.trim(),
            capacity,
            shape.trim(),
            status.trim(),
            active,
            zone.trim(),
            OffsetDateTime.now(ZoneOffset.UTC));

    return repository.save(newTable);
  }

  public TableRecordData updateTable(
      Long tableId,
      String name,
      String branch,
      int capacity,
      String shape,
      String status,
      boolean active,
      String zone) {
    accessPolicy.requireStaffOrManager();

    if (repository.findById(tableId).isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Table not found with id: " + tableId);
    }

    validateTableFields(name, branch, capacity);

    if (repository.existsByNameAndBranch(name, branch, tableId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Table '" + name.trim() + "' already exists in branch '" + branch.trim() + "'.");
    }

    TableRecordData updated =
        new TableRecordData(
            tableId,
            name.trim(),
            branch.trim(),
            capacity,
            shape.trim(),
            status.trim(),
            active,
            zone.trim(),
            OffsetDateTime.now(ZoneOffset.UTC));

    return repository.save(updated);
  }

  public void deleteTable(Long tableId) {
    accessPolicy.requireStaffOrManager();

    if (!repository.deleteById(tableId)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Table not found with id: " + tableId);
    }
  }

  private void validateTableFields(String name, String branch, int capacity) {
    if (name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table name cannot be blank.");
    }
    if (branch.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch cannot be blank.");
    }
    if (capacity <= 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Table capacity must be greater than zero.");
    }
  }
}
