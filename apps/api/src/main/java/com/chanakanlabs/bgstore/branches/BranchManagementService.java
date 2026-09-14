package com.chanakanlabs.bgstore.branches;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import java.time.LocalTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Manager-owned commands for maintaining the store's physical locations. */
@Service
@Transactional
public class BranchManagementService {

  private final BranchJpaRepository branches;
  private final AccessPolicy accessPolicy;

  public BranchManagementService(BranchJpaRepository branches, AccessPolicy accessPolicy) {
    this.branches = branches;
    this.accessPolicy = accessPolicy;
  }

  public Branch createBranch(
      String name,
      @Nullable String address,
      @Nullable LocalTime opensAt,
      @Nullable LocalTime closesAt) {
    accessPolicy.requireManager();

    String cleanName = requiredName(name);
    String cleanAddress = optionalText(address);
    validateHours(opensAt, closesAt);

    if (branches.existsByNameIgnoreCase(cleanName)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A branch with that name already exists.");
    }

    BranchRecord saved =
        branches.save(
            new BranchRecord(UUID.randomUUID(), cleanName, cleanAddress, opensAt, closesAt));
    return saved.toBranch();
  }

  private static String requiredName(String name) {
    String cleanName = name == null ? "" : name.trim();
    if (cleanName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch name cannot be blank.");
    }
    if (cleanName.length() > 120) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Branch name cannot exceed 120 characters.");
    }
    return cleanName;
  }

  private static @Nullable String optionalText(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String cleanValue = value.trim();
    return cleanValue.isBlank() ? null : cleanValue;
  }

  private static void validateHours(@Nullable LocalTime opensAt, @Nullable LocalTime closesAt) {
    if ((opensAt == null) != (closesAt == null)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Opening and closing times must be provided together.");
    }
    if (opensAt != null && !opensAt.isBefore(closesAt)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Opening time must be before closing time.");
    }
  }
}
