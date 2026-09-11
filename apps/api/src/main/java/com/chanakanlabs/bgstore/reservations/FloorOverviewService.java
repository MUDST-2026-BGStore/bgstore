package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.tables.TableManagementService;
import com.chanakanlabs.bgstore.tables.TableManagementService.PageResult;
import com.chanakanlabs.bgstore.tables.TableRecordData;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** The staff floor overview: how many tables are free, in use or held, and when each is booked. */
@Service
public class FloorOverviewService {

  /** One table on the floor with the slots still ahead of it. */
  public record FloorTable(TableRecordData table, List<ReservedSlot> reservedSlots) {}

  /**
   * The status counts cover the whole floor (or branch); only {@code tables} is filtered and paged.
   */
  public record FloorOverview(
      long available, long occupied, long reserved, PageResult<FloorTable> tables) {}

  private final TableManagementService tables;
  private final ReservedSlots reservedSlots;
  private final AccessPolicy accessPolicy;
  private final Clock clock;

  @Autowired
  FloorOverviewService(
      TableManagementService tables, ReservedSlots reservedSlots, AccessPolicy accessPolicy) {
    this(tables, reservedSlots, accessPolicy, Clock.systemUTC());
  }

  FloorOverviewService(
      TableManagementService tables,
      ReservedSlots reservedSlots,
      AccessPolicy accessPolicy,
      Clock clock) {
    this.tables = tables;
    this.reservedSlots = reservedSlots;
    this.accessPolicy = accessPolicy;
    this.clock = clock;
  }

  public FloorOverview overview(
      @Nullable String branch,
      @Nullable String status,
      @Nullable String search,
      int page,
      int pageSize) {
    accessPolicy.requireStaffOrManager();

    PageResult<TableRecordData> rows =
        tables.listTables(branch, null, status, search, page, pageSize);
    Map<String, Long> counts = tables.countByStatus(branch);
    Map<Long, List<ReservedSlot>> slots =
        reservedSlots.upcomingFor(
            rows.items().stream().map(TableRecordData::id).filter(Objects::nonNull).toList(),
            OffsetDateTime.now(clock));

    List<FloorTable> floorTables =
        rows.items().stream()
            .map(table -> new FloorTable(table, slots.getOrDefault(table.id(), List.of())))
            .toList();
    return new FloorOverview(
        counts.getOrDefault("Available", 0L),
        counts.getOrDefault("Occupied", 0L),
        counts.getOrDefault("Reserved", 0L),
        new PageResult<>(
            floorTables, rows.total(), rows.page(), rows.pageSize(), rows.totalPages()));
  }
}
