package com.chanakanlabs.bgstore.tables;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTableRepository implements TableRepository {

  private final Map<Long, TableRecordData> storage = new ConcurrentHashMap<>();
  private final AtomicLong idSequence = new AtomicLong(0);

  public InMemoryTableRepository() {
    seedInitialTables();
  }

  @Override
  public List<TableRecordData> findAll(
      @Nullable String branch,
      @Nullable String zone,
      @Nullable String status,
      @Nullable String search) {
    String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

    return storage.values().stream()
        .filter(
            t -> branch == null || branch.isBlank() || t.branch().equalsIgnoreCase(branch.trim()))
        .filter(
            t ->
                zone == null
                    || zone.isBlank()
                    || "All zones".equalsIgnoreCase(zone.trim())
                    || t.zone().equalsIgnoreCase(zone.trim()))
        .filter(
            t ->
                status == null
                    || status.isBlank()
                    || "All statuses".equalsIgnoreCase(status.trim())
                    || t.status().equalsIgnoreCase(status.trim()))
        .filter(
            t ->
                normalizedSearch.isEmpty()
                    || t.name().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                    || t.zone().toLowerCase(Locale.ROOT).contains(normalizedSearch))
        .sorted(Comparator.comparingLong(t -> t.id() != null ? t.id() : 0L))
        .toList();
  }

  @Override
  public Optional<TableRecordData> findById(Long id) {
    return Optional.ofNullable(storage.get(id));
  }

  @Override
  public TableRecordData save(TableRecordData table) {
    Long id = table.id();
    if (id == null || id <= 0) {
      id = idSequence.incrementAndGet();
    } else if (id > idSequence.get()) {
      idSequence.set(id);
    }

    OffsetDateTime updated =
        table.lastUpdated() != null ? table.lastUpdated() : OffsetDateTime.now(ZoneOffset.UTC);
    TableRecordData saved =
        new TableRecordData(
            id,
            table.name().trim(),
            table.branch().trim(),
            table.capacity(),
            table.shape(),
            table.status(),
            table.active(),
            table.zone().trim(),
            updated);

    storage.put(id, saved);
    return saved;
  }

  @Override
  public boolean deleteById(Long id) {
    return storage.remove(id) != null;
  }

  @Override
  public boolean existsByNameAndBranch(String name, String branch, @Nullable Long excludeId) {
    String normalizedName = name.trim();
    String normalizedBranch = branch.trim();
    return storage.values().stream()
        .filter(t -> excludeId == null || !excludeId.equals(t.id()))
        .anyMatch(
            t ->
                t.branch().equalsIgnoreCase(normalizedBranch)
                    && t.name().equalsIgnoreCase(normalizedName));
  }

  private void seedInitialTables() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // Sukhumvit (Tables 1-10)
    seedTable(
        1L,
        "Table 1",
        "Sukhumvit",
        4,
        "Round",
        "Available",
        true,
        "Main Hall",
        now.minusMinutes(30));
    seedTable(
        2L,
        "Table 2",
        "Sukhumvit",
        2,
        "Square",
        "Available",
        true,
        "Main Hall",
        now.minusMinutes(45));
    seedTable(
        3L,
        "Table 3",
        "Sukhumvit",
        2,
        "Square",
        "Reserved",
        true,
        "Private Room",
        now.minusMinutes(20));
    seedTable(
        4L, "Table 4", "Sukhumvit", 6, "Round", "Available", true, "Main Hall", now.minusHours(1));
    seedTable(
        5L,
        "Table 5",
        "Sukhumvit",
        4,
        "Round",
        "Occupied",
        true,
        "Main Hall",
        now.minusMinutes(10));
    seedTable(
        6L, "Table 6", "Sukhumvit", 8, "Rectangle", "Available", true, "VIP", now.minusHours(2));
    seedTable(
        7L,
        "Table 7",
        "Sukhumvit",
        4,
        "Square",
        "Reserved",
        true,
        "Main Hall",
        now.minusMinutes(50));
    seedTable(
        8L, "Table 8", "Sukhumvit", 2, "Round", "Available", true, "Rooftop", now.minusHours(3));
    seedTable(
        9L, "Table 9", "Sukhumvit", 6, "Oval", "Occupied", true, "Main Hall", now.minusMinutes(15));
    seedTable(
        10L, "Table 10", "Sukhumvit", 10, "Rectangle", "Available", true, "VIP", now.minusHours(4));

    // Silom (Tables 11-15)
    seedTable(
        11L,
        "Table 11",
        "Silom",
        4,
        "Square",
        "Available",
        true,
        "Main Hall",
        now.minusMinutes(40));
    seedTable(
        12L, "Table 12", "Silom", 6, "Round", "Reserved", true, "Main Hall", now.minusMinutes(25));
    seedTable(
        13L, "Table 13", "Silom", 2, "Square", "Available", true, "Rooftop", now.minusHours(2));
    seedTable(
        14L,
        "Table 14",
        "Silom",
        8,
        "Rectangle",
        "Occupied",
        true,
        "Private Room",
        now.minusMinutes(5));
    seedTable(
        15L, "Table 15", "Silom", 4, "Round", "Available", true, "Main Hall", now.minusHours(1));

    // Bangkok (Tables 16-20)
    seedTable(
        16L,
        "Table 16",
        "Bangkok",
        4,
        "Square",
        "Available",
        true,
        "Main Hall",
        now.minusMinutes(35));
    seedTable(
        17L,
        "Table 17",
        "Bangkok",
        2,
        "Round",
        "Unavailable",
        false,
        "Main Hall",
        now.minusDays(1));
    seedTable(18L, "Table 18", "Bangkok", 6, "Oval", "Reserved", true, "VIP", now.minusMinutes(15));
    seedTable(
        19L, "Table 19", "Bangkok", 4, "Round", "Occupied", true, "Main Hall", now.minusMinutes(8));
    seedTable(
        20L,
        "Table 20",
        "Bangkok",
        8,
        "Rectangle",
        "Available",
        true,
        "Rooftop",
        now.minusHours(5));
  }

  private void seedTable(
      Long id,
      String name,
      String branch,
      int capacity,
      String shape,
      String status,
      boolean active,
      String zone,
      OffsetDateTime updated) {
    save(new TableRecordData(id, name, branch, capacity, shape, status, active, zone, updated));
  }
}
