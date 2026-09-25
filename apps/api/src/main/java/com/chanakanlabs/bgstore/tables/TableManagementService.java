package com.chanakanlabs.bgstore.tables;

import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.branches.BranchDirectory;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.identity.ApplicationRole;
import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TableManagementService {

  public record PageResult<T>(List<T> items, int total, int page, int pageSize, int totalPages) {}

  private final TableRepository repository;
  private final AccessPolicy accessPolicy;
  private final BranchDirectory branches;

  TableManagementService(
      TableRepository repository, AccessPolicy accessPolicy, BranchDirectory branches) {
    this.repository = repository;
    this.accessPolicy = accessPolicy;
    this.branches = branches;
  }

  public PageResult<TableRecordData> listTables(
      @Nullable String branch,
      @Nullable String status,
      @Nullable String search,
      int page,
      int pageSize) {
    var identity = accessPolicy.requireStaffOrManager();
    var rows =
        repository.findAll(
            canonicalBranchForRead(branch, identity), status, trimmed(search), false);
    return pageOf(scopeRows(rows, identity), page, pageSize);
  }

  /** The tables in service, which are the ones the floor overview shows. */
  public PageResult<TableRecordData> listActiveTables(
      @Nullable String branch,
      @Nullable String status,
      @Nullable String search,
      int page,
      int pageSize) {
    var identity = accessPolicy.requireStaffOrManager();
    var rows =
        repository.findAll(canonicalBranchForRead(branch, identity), status, trimmed(search), true);
    return pageOf(scopeRows(rows, identity), page, pageSize);
  }

  /**
   * How many tables in service are in each status, across every branch when {@code branch} is null.
   */
  public Map<String, Long> countActiveByStatus(@Nullable String branch) {
    var identity = accessPolicy.requireStaffOrManager();
    var rows = repository.findAll(canonicalBranchForRead(branch, identity), null, null, true);
    return scopeRows(rows, identity).stream()
        .collect(
            java.util.stream.Collectors.groupingBy(
                TableRecordData::status, java.util.stream.Collectors.counting()));
  }

  private static PageResult<TableRecordData> pageOf(
      List<TableRecordData> all, int page, int pageSize) {
    int total = all.size();
    int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
    int safePage = Math.max(1, Math.min(page, totalPages));
    int fromIndex = Math.min((safePage - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);

    List<TableRecordData> items = all.subList(fromIndex, toIndex);
    return new PageResult<>(items, total, safePage, pageSize, totalPages);
  }

  private static @Nullable String trimmed(@Nullable String search) {
    return search == null ? null : search.trim();
  }

  public TableRecordData getTable(Long tableId) {
    accessPolicy.requireStaffOrManager();
    var table =
        repository
            .findById(tableId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Table not found with id: " + tableId));
    requireTableBranch(table);
    return table;
  }

  public TableRecordData createTable(
      String name, String branch, int capacity, String shape, String status, boolean active) {
    accessPolicy.requireStaffOrManager();

    validateTableFields(name, branch, capacity);
    Branch selectedBranch = requireKnownBranch(branch);
    accessPolicy.requireBranch(selectedBranch.id());

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
            OffsetDateTime.now(ZoneOffset.UTC),
            selectedBranch.id());

    return repository.save(newTable);
  }

  public TableRecordData updateTable(
      Long tableId,
      String name,
      String branch,
      int capacity,
      String shape,
      String status,
      boolean active) {
    accessPolicy.requireStaffOrManager();

    TableRecordData existing = repository.findById(tableId).orElse(null);
    if (existing == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Table not found with id: " + tableId);
    }

    validateTableFields(name, branch, capacity);
    requireTableBranch(existing);
    Branch selectedBranch = requireKnownBranch(branch);
    accessPolicy.requireBranch(selectedBranch.id());

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
            OffsetDateTime.now(ZoneOffset.UTC),
            selectedBranch.id());

    return repository.save(updated);
  }

  public void deleteTable(Long tableId) {
    accessPolicy.requireStaffOrManager();
    TableRecordData existing = repository.findById(tableId).orElse(null);
    if (existing == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Table not found with id: " + tableId);
    }
    requireTableBranch(existing);
    repository.deleteById(tableId);
  }

  private @Nullable String canonicalBranchForRead(
      @Nullable String requested, AuthenticatedIdentity identity) {
    if (requested != null && !requested.isBlank()) {
      Branch found = requireKnownBranch(requested);
      accessPolicy.requireBranch(found.id());
      return found.name();
    }
    if (identity.roles().contains(ApplicationRole.MANAGER)) return null;
    accessPolicy.requireAnyAssignedBranch();
    return null;
  }

  private List<TableRecordData> scopeRows(
      List<TableRecordData> rows, AuthenticatedIdentity identity) {
    if (identity.roles().contains(ApplicationRole.MANAGER)) return rows;
    return rows.stream()
        .filter(
            row -> {
              Branch branch = branchFor(row);
              return accessPolicy.canAccessBranch(branch.id());
            })
        .toList();
  }

  private Branch requireKnownBranch(String name) {
    return branches.findAll().stream()
        .filter(found -> found.name().equalsIgnoreCase(name.trim()))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown branch."));
  }

  private Branch branchFor(TableRecordData table) {
    if (table.branchId() != null) {
      return branches
          .findById(table.branchId())
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown table branch."));
    }
    return requireKnownBranch(table.branch());
  }

  private void requireTableBranch(TableRecordData table) {
    Branch branch = branchFor(table);
    accessPolicy.requireBranch(branch.id());
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
