package com.chanakanlabs.bgstore.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The composite key of {@code game_branch_stock}. */
@Embeddable
@SuppressWarnings("NullAway.Init")
class GameBranchStockId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "game_id", nullable = false)
  private UUID gameId;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  protected GameBranchStockId() {}

  GameBranchStockId(UUID gameId, UUID branchId) {
    this.gameId = gameId;
    this.branchId = branchId;
  }

  UUID gameId() {
    return gameId;
  }

  UUID branchId() {
    return branchId;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof GameBranchStockId id
        && gameId.equals(id.gameId)
        && branchId.equals(id.branchId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gameId, branchId);
  }
}
