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

  @Column(name = "edition_language", nullable = false, length = 35)
  private String editionLanguage;

  /** The edition's catalog game at placement; null on lines written before it was recorded. */
  @Column(name = "game_id")
  private java.util.UUID gameId;

  /** Snapshots the name, language and unit price the stock hold was priced at. */
  public static OrderItem snapshot(
      java.util.UUID orderId,
      java.util.UUID editionId,
      java.util.UUID gameId,
      String gameName,
      String editionLanguage,
      int quantity,
      long unitPriceMinor) {
    if (quantity < 1 || unitPriceMinor < 0)
      throw new IllegalArgumentException("Invalid order line");
    var item = new OrderItem();
    item.id =
        new OrderItemId(
            java.util.Objects.requireNonNull(orderId), java.util.Objects.requireNonNull(editionId));
    item.gameId = java.util.Objects.requireNonNull(gameId);
    item.gameName = java.util.Objects.requireNonNull(gameName);
    item.editionLanguage = java.util.Objects.requireNonNull(editionLanguage);
    item.quantity = quantity;
    item.unitPriceMinor = unitPriceMinor;
    item.lineTotalMinor = Math.multiplyExact(unitPriceMinor, (long) quantity);
    return item;
  }
}
