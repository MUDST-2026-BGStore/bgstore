package com.chanakanlabs.bgstore.identity;

import java.util.UUID;

/** Identity's inbound port for validating branch ids without depending on the branches module. */
public interface BranchAccessPort {
  boolean exists(UUID branchId);
}
