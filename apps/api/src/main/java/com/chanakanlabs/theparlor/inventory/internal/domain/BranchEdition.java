package com.chanakanlabs.theparlor.inventory.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
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
  // TODO(inventory-domain): add create/changePrice/adjustStock/hold/release/fulfill methods.
  // Start with zero on-hand/held, require price>=0 and preserve 0<=held<=onHand with exact
  // arithmetic after every mutation. Call under the application service's stock-row lock.
  // Keep these fields private; no setters or direct persistence updates around domain methods.
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
}
