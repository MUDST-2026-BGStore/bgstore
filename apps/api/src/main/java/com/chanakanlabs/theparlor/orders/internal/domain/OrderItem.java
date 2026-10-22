package com.chanakanlabs.theparlor.orders.internal.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
  /**
   * Maximum historical game name snapshot length; owned by orders, independent of live catalog
   * edits.
   */
  public static final int MAX_GAME_NAME_LENGTH = 200;

  @EmbeddedId private OrderItemId id;

  @Column(name = "game_name", nullable = false, length = MAX_GAME_NAME_LENGTH)
  private String gameName;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price_minor", nullable = false)
  private Long unitPriceMinor;

  @Column(name = "line_total_minor", nullable = false)
  private Long lineTotalMinor;
}
