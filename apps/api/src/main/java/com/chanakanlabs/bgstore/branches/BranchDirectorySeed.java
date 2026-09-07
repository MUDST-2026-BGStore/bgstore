package com.chanakanlabs.bgstore.branches;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The stores BGStore operates. Reference data rather than sample data: the game screens cannot
 * record copies without a branch to record them against.
 *
 * <p>This ran as an {@code insert} inside the schema migration until Hibernate took the schema
 * over. {@code ddl-auto} creates tables but never rows, so the directory is seeded here instead, on
 * every start and only for the branches that are missing.
 */
@Component
class BranchDirectorySeed implements ApplicationRunner {

  private static final Map<UUID, String> BRANCHES = new LinkedHashMap<>();

  static {
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000001"), "Central Rama II");
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000002"), "Big C Rama I");
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000003"), "Big C Rama IX");
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000004"), "Sukhumvit");
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000005"), "Silom");
    BRANCHES.put(UUID.fromString("3f0d7d5a-9a2b-4a71-8f0e-000000000006"), "Thonglor");
  }

  private final BranchJpaRepository branches;

  BranchDirectorySeed(BranchJpaRepository branches) {
    this.branches = branches;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    BRANCHES.forEach(
        (id, name) -> {
          if (!branches.existsById(id)) {
            branches.save(new BranchRecord(id, name));
          }
        });
  }
}
