package com.chanakanlabs.theparlor.carts.internal.domain;

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
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "account_id")
  private UUID accountId;

  @Column(name = "token_hash", nullable = false)
  @Getter(AccessLevel.NONE)
  private byte[] tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.STATUS)
  private CartStatus status;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "fulfillment_method",
      nullable = false,
      length = EnumStorageLengths.FULFILLMENT_METHOD)
  private CartFulfillmentMethod fulfillmentMethod;

  @Column(name = "pickup_branch_id")
  private UUID pickupBranchId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public byte[] getTokenHash() {
    return tokenHash == null ? null : tokenHash.clone();
  }
}
