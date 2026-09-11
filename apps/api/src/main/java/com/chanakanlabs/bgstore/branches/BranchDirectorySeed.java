package com.chanakanlabs.bgstore.branches;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The stores BGStore operates. Reference data rather than sample data: the game screens cannot
 * record copies without a branch to record them against.
 *
 * <p>This ran as an {@code insert} inside the schema migration until the directory became a JPA
 * module. The seed remains idempotent: it only fills missing reference rows and missing details,
 * never overwriting what a branch already records.
 */
@Component
class BranchDirectorySeed implements ApplicationRunner {

  private record Seed(
      String name,
      @Nullable String address,
      @Nullable LocalTime opensAt,
      @Nullable LocalTime closesAt) {

    Seed(String name) {
      this(name, null, null, null);
    }
  }

  private static final Map<UUID, Seed> BRANCHES = new LinkedHashMap<>();

  static {
    BRANCHES.put(
        UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000001"),
        new Seed(
            "Central Rama II",
            "160 ถ. พระรามที่ 2 แขวงแสมดำ เขตบางขุนเทียน กรุงเทพฯ 10150",
            LocalTime.of(9, 0),
            LocalTime.of(19, 0)));
    BRANCHES.put(
        UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000002"),
        new Seed(
            "Big C Rama I",
            "999/9 ถ. พระรามที่ 1 แขวงปทุมวัน เขตปทุมวัน กรุงเทพฯ 10330",
            LocalTime.of(10, 0),
            LocalTime.of(20, 0)));
    BRANCHES.put(
        UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000003"),
        new Seed(
            "Big C Rama IX",
            "999/9 ถ. พระรามที่ 9 แขวงห้วยขวาง เขตห้วยขวาง กรุงเทพฯ 10310",
            LocalTime.of(10, 0),
            LocalTime.of(21, 0)));
    // The design gives these three no address or hours yet.
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000004"), new Seed("Sukhumvit"));
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000005"), new Seed("Silom"));
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000006"), new Seed("Thonglor"));
  }

  private final BranchJpaRepository branches;

  BranchDirectorySeed(BranchJpaRepository branches) {
    this.branches = branches;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    BRANCHES.forEach(
        (id, seed) -> {
          var branch = branches.findById(id).orElseGet(() -> new BranchRecord(id, seed.name()));
          branch.fillMissingDetails(seed.address(), seed.opensAt(), seed.closesAt());
          branches.save(branch);
        });
  }
}
