package com.chanakanlabs.theparlor.branches;

import java.util.UUID;

public interface BranchAccess {
  void requireActive(UUID branchId);

  String requireOperator(UUID branchId);
}
