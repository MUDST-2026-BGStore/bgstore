package com.chanakanlabs.theparlor.branches;

import java.util.Optional;
import java.util.UUID;

public interface BranchAccess {
  void requireActive(UUID branchId);

  String requireOperator(UUID branchId);

  /** The active branch that ships delivery orders, if one is designated. */
  Optional<UUID> deliveryBranch();

  /** Whether the branch exists and is open for business. */
  boolean isActive(UUID branchId);
}
