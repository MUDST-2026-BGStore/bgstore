package com.chanakanlabs.bgstore.tables;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.lang.Nullable;

/** Small deterministic test fixture; production persistence is covered by JpaTableRepository. */
final class TestTableRepository implements TableRepository {

  private final Map<Long, TableRecordData> tables = new HashMap<>();

  TestTableRepository() {
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    save(
        new TableRecordData(
            1L, "Table 1", "Sukhumvit", 4, "Round", "Available", true, "Main Hall", now));
    save(
        new TableRecordData(
            12L, "Table 12", "Silom", 6, "Round", "Reserved", true, "Main Hall", now));
  }

  @Override
  public List<TableRecordData> findAll(
      @Nullable String branch,
      @Nullable String zone,
      @Nullable String status,
      @Nullable String search) {
    var normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    return tables.values().stream()
        .filter(
            t -> branch == null || branch.isBlank() || t.branch().equalsIgnoreCase(branch.trim()))
        .filter(t -> zone == null || zone.isBlank() || t.zone().equalsIgnoreCase(zone.trim()))
        .filter(
            t -> status == null || status.isBlank() || t.status().equalsIgnoreCase(status.trim()))
        .filter(
            t ->
                normalizedSearch.isEmpty()
                    || t.name().toLowerCase(Locale.ROOT).contains(normalizedSearch))
        .sorted(Comparator.comparingLong(t -> t.id() == null ? 0 : t.id()))
        .toList();
  }

  @Override
  public Optional<TableRecordData> findById(Long id) {
    return Optional.ofNullable(tables.get(id));
  }

  @Override
  public TableRecordData save(TableRecordData table) {
    var id = table.id() == null ? nextId() : table.id();
    var saved =
        new TableRecordData(
            id,
            table.name(),
            table.branch(),
            table.capacity(),
            table.shape(),
            table.status(),
            table.active(),
            table.zone(),
            table.lastUpdated());
    tables.put(id, saved);
    return saved;
  }

  @Override
  public boolean deleteById(Long id) {
    return tables.remove(id) != null;
  }

  @Override
  public boolean existsByNameAndBranch(String name, String branch, @Nullable Long excludeId) {
    return tables.values().stream()
        .filter(t -> excludeId == null || !excludeId.equals(t.id()))
        .anyMatch(
            t ->
                t.name().equalsIgnoreCase(name.trim())
                    && t.branch().equalsIgnoreCase(branch.trim()));
  }

  private long nextId() {
    return tables.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1;
  }
}
