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

  /**
   * Opens an empty cart. It starts as a delivery cart because pickup needs a branch the shopper has
   * not chosen yet. tokenHash is the SHA-256 of the bearer token; the token is never stored.
   */
  public static Cart open(UUID accountId, byte[] tokenHash) {
    var cart = new Cart();
    cart.accountId = accountId;
    cart.status = CartStatus.OPEN;
    cart.fulfillmentMethod = CartFulfillmentMethod.SHIPPING;
    cart.rotateToken(tokenHash);
    return cart;
  }

  public boolean isOpen() {
    return status == CartStatus.OPEN;
  }

  public boolean isShipping() {
    return fulfillmentMethod == CartFulfillmentMethod.SHIPPING;
  }

  /** Replaces the bearer token; whoever held the previous one loses access. */
  public void rotateToken(byte[] tokenHash) {
    if (tokenHash == null || tokenHash.length != 32)
      throw new IllegalArgumentException("Cart token hash must be a SHA-256 digest");
    this.tokenHash = tokenHash.clone();
  }

  public void pickUpAt(UUID branchId) {
    requireOpen();
    pickupBranchId = java.util.Objects.requireNonNull(branchId);
    fulfillmentMethod = CartFulfillmentMethod.PICKUP;
  }

  public void ship() {
    requireOpen();
    pickupBranchId = null;
    fulfillmentMethod = CartFulfillmentMethod.SHIPPING;
  }

  /** The cart became an order. It keeps its lines as a record and can never change again. */
  public void convert() {
    requireOpen();
    status = CartStatus.CONVERTED;
  }

  /** Gives the cart up and replaces its token with one nobody holds. */
  public void abandon(byte[] unknownTokenHash) {
    requireOpen();
    status = CartStatus.ABANDONED;
    rotateToken(unknownTokenHash);
  }

  private void requireOpen() {
    if (!isOpen()) throw new IllegalStateException("Cart is no longer open");
  }
}
