package com.chanakanlabs.theparlor.pantry.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** One entry of the append-only pantry receipt and correction ledger. */
@Entity
@Table(name = "menu_stock_adjustments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuStockAdjustment {
  /** Matches StockAdjustmentRequest.reason. */
  public static final int MAX_REASON_LENGTH = 500;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "menu_item_id", nullable = false, updatable = false)
  private UUID menuItemId;

  @Column(nullable = false, updatable = false)
  private int delta;

  @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
  private String reason;

  @Column(name = "operator_subject", nullable = false, updatable = false)
  private String operatorSubject;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static MenuStockAdjustment record(
      BranchMenuStockId stock, int delta, String reason, String actor) {
    if (delta == 0
        || reason == null
        || reason.isBlank()
        || reason.strip().length() > MAX_REASON_LENGTH
        || actor == null
        || actor.isBlank()) throw new IllegalArgumentException("Invalid stock adjustment");
    var adjustment = new MenuStockAdjustment();
    adjustment.branchId = Objects.requireNonNull(stock.branchId());
    adjustment.menuItemId = Objects.requireNonNull(stock.menuItemId());
    adjustment.delta = delta;
    adjustment.reason = reason.strip();
    adjustment.operatorSubject = actor;
    return adjustment;
  }
}
