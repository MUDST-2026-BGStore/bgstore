package com.chanakanlabs.bgstore.inventory;

import java.util.UUID;

/** Copies of one game held at one branch. */
record BranchStockRow(UUID branchId, int copies, int inUse) {

  int available() {
    return copies - inUse;
  }
}
