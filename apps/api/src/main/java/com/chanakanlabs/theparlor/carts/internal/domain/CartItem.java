package com.chanakanlabs.theparlor.carts.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@DynamicUpdate
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {
  @EmbeddedId private CartItemId id;

  @Column(nullable = false)
  private int quantity;

  @CreationTimestamp
  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Maximum units of one edition in a cart, matching the API contract and storage. */
  public static final int MAX_QUANTITY = 99;

  public static CartItem of(java.util.UUID cartId, java.util.UUID editionId, int quantity) {
    var item = new CartItem();
    item.id =
        new CartItemId(
            java.util.Objects.requireNonNull(cartId), java.util.Objects.requireNonNull(editionId));
    item.changeQuantity(quantity);
    return item;
  }

  public void changeQuantity(int quantity) {
    if (quantity < 1 || quantity > MAX_QUANTITY)
      throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_QUANTITY);
    this.quantity = quantity;
  }
}
