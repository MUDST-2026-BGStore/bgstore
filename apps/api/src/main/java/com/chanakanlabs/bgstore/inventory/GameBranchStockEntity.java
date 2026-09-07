package com.chanakanlabs.bgstore.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The {@code game_branch_stock} table, created from this class by {@code ddl-auto}. */
@Entity
@Table(name = "game_branch_stock")
@SuppressWarnings("NullAway.Init")
class GameBranchStockEntity {

  @EmbeddedId private GameBranchStockId id;

  @Column(name = "copies", nullable = false)
  private int copies;

  /**
   * Maintained by the play-session module when copies are assigned to a session; availability is
   * copies minus this.
   */
  @Column(name = "copies_in_use", nullable = false)
  private int copiesInUse;

  protected GameBranchStockEntity() {}

  GameBranchStockEntity(GameBranchStockId id, int copies) {
    this.id = id;
    this.copies = copies;
  }

  void setCopies(int copies) {
    this.copies = copies;
  }

  BranchStockRow toRow() {
    return new BranchStockRow(id.branchId(), copies, copiesInUse);
  }
}
