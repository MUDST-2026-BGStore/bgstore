package com.chanakanlabs.theparlor.inventory.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@DynamicUpdate
@Table(name = "branch_editions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchEdition {
  @EmbeddedId private BranchEditionId id;

  @Column(name = "sale_price_minor", nullable = false)
  private Long salePriceMinor;

  @Column(name = "stock_on_hand", nullable = false)
  private int saleOnHand;

  @Column(name = "stock_held", nullable = false)
  private int saleHeld;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static BranchEdition create(BranchEditionId id, long priceMinor) {
    var edition = new BranchEdition();
    edition.id = Objects.requireNonNull(id);
    Objects.requireNonNull(id.branchId());
    Objects.requireNonNull(id.editionId());
    edition.changePrice(priceMinor);
    return edition;
  }

  public void changePrice(long priceMinor) {
    if (priceMinor < 0) throw new IllegalArgumentException("Price must be nonnegative");
    salePriceMinor = priceMinor;
  }

  public void adjustStock(int delta) {
    if (delta == 0) throw new IllegalArgumentException("Stock adjustment must be nonzero");
    int adjusted = Math.addExact(saleOnHand, delta);
    if (adjusted < saleHeld)
      throw new IllegalArgumentException("Stock cannot fall below held units");
    saleOnHand = adjusted;
  }

  public void hold(int quantity) {
    requirePositive(quantity);
    int held = Math.addExact(saleHeld, quantity);
    if (held > saleOnHand) throw new IllegalArgumentException("Insufficient stock");
    saleHeld = held;
  }

  public void release(int quantity) {
    requirePositive(quantity);
    if (quantity > saleHeld) throw new IllegalArgumentException("Insufficient held stock");
    saleHeld -= quantity;
  }

  public void fulfill(int quantity) {
    release(quantity);
    saleOnHand -= quantity;
  }

  private static void requirePositive(int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
  }
}
