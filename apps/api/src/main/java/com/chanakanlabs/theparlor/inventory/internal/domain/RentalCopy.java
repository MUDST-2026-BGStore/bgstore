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
  // TODO(inventory-domain): add register/changeOperationalStatus methods; require IDs and a
  // nonblank bounded asset code, start USABLE and prohibit transitions out of RETIRED.
  // The application service owns assignment/booking conflict checks and condition-change audit.
  /** Stored asset code width; the existing request contract currently allows 80. */
  public static final int MAX_ASSET_CODE_LENGTH = 64;

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
}
