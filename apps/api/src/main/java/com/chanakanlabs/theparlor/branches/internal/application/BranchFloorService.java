package com.chanakanlabs.theparlor.branches.internal.application;

import com.chanakanlabs.theparlor.branches.BranchFloor;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchOpeningHours;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchTable;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchOpeningHoursRepository;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchRepository;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchTableRepository;
import com.chanakanlabs.theparlor.branches.spi.WeeklyHours;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class BranchFloorService implements BranchFloor {
  private final BranchTableRepository tables;
  private final BranchOpeningHoursRepository openingHours;
  private final BranchRepository branches;
  private final EntityManager entityManager;

  BranchFloorService(
      BranchTableRepository tables,
      BranchOpeningHoursRepository openingHours,
      BranchRepository branches,
      EntityManager entityManager) {
    this.tables = tables;
    this.openingHours = openingHours;
    this.branches = branches;
    this.entityManager = entityManager;
  }

  @Override
  public List<TableInfo> tables(UUID branchId) {
    return tables.findByBranchIdOrderById(branchId).stream().map(BranchFloorService::info).toList();
  }

  @Override
  @Transactional
  public Optional<TableInfo> lockTable(UUID branchId, UUID tableId) {
    // Branch edits and bookings share this lock; acquire it before the table lock.
    var branch =
        branches
            .lockById(branchId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
    // requireActive may have loaded this entity before a concurrent edit committed.
    entityManager.refresh(branch);
    if (!branch.isActive())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
    return tables.lockByIdAndBranchId(tableId, branchId).map(BranchFloorService::info);
  }

  @Override
  public WeeklyHours openingHours(UUID branchId) {
    return new WeeklyHours(
        openingHours.findByIdBranchIdIn(List.of(branchId)).stream()
            .map(BranchOpeningHours::toPeriod)
            .toList());
  }

  private static TableInfo info(BranchTable table) {
    return new TableInfo(
        table.getId(),
        table.getLabel(),
        table.getSeats(),
        table.getHourlyRateMinor(),
        table.isInService());
  }
}
