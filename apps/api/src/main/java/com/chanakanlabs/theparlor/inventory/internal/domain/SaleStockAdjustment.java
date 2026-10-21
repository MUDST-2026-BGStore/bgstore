package com.chanakanlabs.theparlor.inventory.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "sale_stock_adjustments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleStockAdjustment {
  /** Maximum inventory adjustment reason length, matching StockAdjustmentRequest. */
  public static final int MAX_REASON_LENGTH = 500;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "edition_id", nullable = false)
  private UUID editionId;

  @Column(nullable = false)
  private int delta;

  @Column(nullable = false, length = MAX_REASON_LENGTH)
  private String reason;

  @Column(name = "operator_subject", nullable = false)
  private String operatorSubject;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
