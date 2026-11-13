package com.chanakanlabs.theparlor.pantry.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

/** A branch's units of one menu item. Held units belong to confirmed reservation pre-orders. */
@Entity
@DynamicUpdate
@Table(name = "branch_menu_stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchMenuStock {
  @EmbeddedId private BranchMenuStockId id;

  @Column(name = "on_hand", nullable = false)
  private int onHand;

  @Column(nullable = false)
  private int held;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** A branch starts stocking an item with nothing on hand. */
  public static BranchMenuStock empty(BranchMenuStockId id) {
    var stock = new BranchMenuStock();
    stock.id = Objects.requireNonNull(id);
    Objects.requireNonNull(id.branchId());
    Objects.requireNonNull(id.menuItemId());
    return stock;
  }

  public int available() {
    return onHand - held;
  }

  public void adjust(int delta) {
    if (delta == 0) throw new IllegalArgumentException("Stock adjustment must be nonzero");
    int adjusted = Math.addExact(onHand, delta);
    if (adjusted < held) throw new IllegalArgumentException("Stock cannot fall below held units");
    onHand = adjusted;
  }

  public void hold(int quantity) {
    requirePositive(quantity);
    int total = Math.addExact(held, quantity);
    if (total > onHand) throw new IllegalArgumentException("Insufficient stock");
    held = total;
  }

  public void release(int quantity) {
    requirePositive(quantity);
    if (quantity > held) throw new IllegalArgumentException("Insufficient held stock");
    held -= quantity;
  }

  public void fulfill(int quantity) {
    release(quantity);
    onHand -= quantity;
  }

  private static void requirePositive(int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
  }
}
