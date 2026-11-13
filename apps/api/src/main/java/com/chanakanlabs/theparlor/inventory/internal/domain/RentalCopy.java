package com.chanakanlabs.theparlor.inventory.internal.domain;

import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "rental_copies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalCopy {
  public static final int MAX_ASSET_CODE_LENGTH = 80;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "game_id", nullable = false)
  private UUID gameId;

  @Column(name = "asset_code", nullable = false, length = MAX_ASSET_CODE_LENGTH)
  private String assetCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "operational_status", nullable = false, length = EnumStorageLengths.STATUS)
  private RentalCopyOperationalStatus operationalStatus;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static RentalCopy register(UUID branchId, UUID gameId, String assetCode) {
    if (assetCode == null || assetCode.isBlank() || assetCode.length() > MAX_ASSET_CODE_LENGTH)
      throw new IllegalArgumentException("Invalid asset code");
    var copy = new RentalCopy();
    copy.branchId = java.util.Objects.requireNonNull(branchId);
    copy.gameId = java.util.Objects.requireNonNull(gameId);
    copy.assetCode = assetCode.strip();
    copy.operationalStatus = RentalCopyOperationalStatus.USABLE;
    return copy;
  }

  /**
   * Records a new physical condition. Retirement is terminal. Callers must first move every open
   * commitment off a copy that leaves the rentable pool.
   */
  public void changeCondition(RentalCopyOperationalStatus status) {
    java.util.Objects.requireNonNull(status);
    if (operationalStatus == RentalCopyOperationalStatus.RETIRED && status != operationalStatus)
      throw new IllegalStateException("A retired copy cannot be reactivated");
    operationalStatus = status;
  }

  public boolean isUsable() {
    return operationalStatus == RentalCopyOperationalStatus.USABLE;
  }
}
