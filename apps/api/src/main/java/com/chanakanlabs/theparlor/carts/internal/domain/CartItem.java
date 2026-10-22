package com.chanakanlabs.theparlor.carts.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
