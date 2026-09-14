package com.chanakanlabs.bgstore.branches;

import com.chanakanlabs.bgstore.identity.BranchAccessPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapts branch ownership to the identity module's validation port. */
@Component
class BranchAccessAdapter implements BranchAccessPort {
  private final BranchDirectory branches;

  BranchAccessAdapter(BranchDirectory branches) {
    this.branches = branches;
  }

  @Override
  public boolean exists(UUID branchId) {
    return branches.findById(branchId).isPresent();
  }
}
