package com.chanakanlabs.theparlor.branches.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchTable;
import com.chanakanlabs.theparlor.branches.internal.domain.TableOperationalStatus;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchTableRepository;
import com.chanakanlabs.theparlor.branches.spi.TableCommitments;
import com.chanakanlabs.theparlor.contracts.model.CreateTableRequest;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.Table;
import com.chanakanlabs.theparlor.contracts.model.TablePage;
import com.chanakanlabs.theparlor.contracts.model.TableState;
import com.chanakanlabs.theparlor.contracts.model.UpdateTableRequest;
import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Branch play tables: configuration is stored here, current state is derived per read. */
@Service
@Transactional(readOnly = true)
public class TablesService {
  private static final String LIST = "tables";

  private final BranchAccess access;
  private final BranchTableRepository tables;
  private final List<TableCommitments> commitments;
  private final Clock clock;

  public TablesService(
      BranchAccess access,
      BranchTableRepository tables,
      List<TableCommitments> commitments,
      Clock clock) {
    this.access = access;
    this.tables = tables;
    this.commitments = commitments;
    this.clock = clock;
  }

  public Table get(UUID branchId, UUID tableId) {
    access.requireActive(branchId);
    var table = tables.findByIdAndBranchId(tableId, branchId).orElseThrow(TablesService::missing);
    return toContract(table, new Occupancy(branchId));
  }

  public TablePage list(UUID branchId, Integer requestedLimit, String cursor) {
    access.requireActive(branchId);
    int limit = KeysetCursor.limit(requestedLimit);
    String filters = branchId.toString();
    UUID after = KeysetCursor.afterId(cursor, LIST, filters);
    Specification<BranchTable> inBranch =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("branchId"), branchId),
                after == null ? cb.conjunction() : cb.greaterThan(root.get("id"), after));
    var rows = tables.findBy(inBranch, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var occupancy = new Occupancy(branchId);
    var page = rows.stream().limit(limit).toList();
    var result = new TablePage(page.stream().map(table -> toContract(table, occupancy)).toList());
    if (rows.size() > limit)
      result.setNextCursor(KeysetCursor.next(LIST, filters, page.getLast().getId()));
    return result;
  }

  @Transactional
  public Table create(UUID branchId, CreateTableRequest request) {
    access.requireOperator(branchId);
    var table =
        BranchTable.create(
            branchId,
            request.getLabel(),
            request.getCapacity(),
            request.getHourlyRate().getAmountMinor(),
            TableOperationalStatus.valueOf(request.getOperationalStatus().name()));
    save(table);
    return toContract(table, new Occupancy(branchId));
  }

  @Transactional
  public Table update(UUID branchId, UUID tableId, UpdateTableRequest request) {
    access.requireOperator(branchId);
    if (request.getLabel() == null
        && request.getCapacity() == null
        && request.getHourlyRate() == null
        && request.getOperationalStatus() == null)
      throw new IllegalArgumentException("At least one update property is required");
    // The row lock serializes this change against bookings, which lock the table they commit.
    var table = tables.lockByIdAndBranchId(tableId, branchId).orElseThrow(TablesService::missing);

    int seats = request.getCapacity() == null ? table.getSeats() : request.getCapacity();
    if (seats < table.getSeats()
        && commitments.stream().anyMatch(c -> c.hasPartyLargerThan(tableId, seats)))
      throw conflict("Capacity is below a confirmed reservation or active rental");
    table.configure(
        request.getLabel() == null ? table.getLabel() : request.getLabel(),
        seats,
        request.getHourlyRate() == null
            ? table.getHourlyRateMinor()
            : request.getHourlyRate().getAmountMinor());

    if (request.getOperationalStatus() != null) {
      var status = TableOperationalStatus.valueOf(request.getOperationalStatus().name());
      if (status == TableOperationalStatus.OUT_OF_SERVICE
          && table.isInService()
          && commitments.stream().anyMatch(c -> c.hasPartyLargerThan(tableId, 0)))
        throw conflict("Table has a confirmed reservation or active rental");
      table.changeOperationalStatus(status);
    }
    save(table);
    return toContract(table, new Occupancy(branchId));
  }

  private void save(BranchTable table) {
    try {
      tables.saveAndFlush(table);
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Table label already exists at this branch", exception);
    }
  }

  /** A snapshot of the branch's committed tables at one server instant. */
  private final class Occupancy {
    private final Set<UUID> occupied = new HashSet<>();
    private final Set<UUID> reserved = new HashSet<>();

    Occupancy(UUID branchId) {
      var now = clock.instant();
      for (TableCommitments source : commitments) {
        occupied.addAll(source.occupiedTables(branchId));
        reserved.addAll(source.reservedTables(branchId, now));
      }
    }

    TableState stateOf(BranchTable table) {
      if (occupied.contains(table.getId())) return TableState.OCCUPIED;
      if (!table.isInService()) return TableState.OUT_OF_SERVICE;
      return reserved.contains(table.getId()) ? TableState.RESERVED : TableState.AVAILABLE;
    }
  }

  private static Table toContract(BranchTable table, Occupancy occupancy) {
    return new Table(
        table.getId(),
        table.getBranchId(),
        table.getLabel(),
        table.getSeats(),
        new Money(table.getHourlyRateMinor()),
        Table.OperationalStatusEnum.valueOf(table.getOperationalStatus().name()),
        occupancy.stateOf(table));
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
  }

  private static ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }
}
