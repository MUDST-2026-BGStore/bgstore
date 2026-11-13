package com.chanakanlabs.theparlor.orders.internal.domain;

import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.orders.internal.domain.Order.Buyer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTests {
  static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");
  static final Buyer GUEST =
      new Buyer(null, null, " Guest ", "guest@example.invalid", "+66800000000");

  static Order placed(Map<String, Object> address) {
    return Order.place(UUID.randomUUID(), GUEST, OrderSource.CUSTOMER, address, " ", 5000, "guest");
  }

  @Test
  void placesPickupOrShippingFromWhetherAnAddressIsGiven() {
    var pickup = placed(null);
    assertThat(pickup.getStatus()).isEqualTo(OrderStatus.PLACED);
    assertThat(pickup.getFulfillmentMethod()).isEqualTo(OrderFulfillmentMethod.PICKUP);
    assertThat(pickup.getContactName()).isEqualTo("Guest");
    assertThat(pickup.getNotes()).isNull();
    assertThat(placed(Map.of("line1", "1 Road")).getFulfillmentMethod())
        .isEqualTo(OrderFulfillmentMethod.SHIPPING);
  }

  @Test
  void cancellationAndFulfillmentAreTerminalAndRepeatable() {
    var cancelled = placed(null);
    assertThat(cancelled.cancel("staff-1", " Changed mind ", NOW)).isTrue();
    assertThat(cancelled.cancel("staff-2", "Again", NOW.plusSeconds(60))).isFalse();
    assertThat(cancelled.getCancelledBy()).isEqualTo("staff-1");
    assertThat(cancelled.getCancellationReason()).isEqualTo("Changed mind");
    assertThatThrownBy(() -> cancelled.fulfill("staff-1", NOW))
        .isInstanceOf(IllegalStateException.class);

    var fulfilled = placed(null);
    assertThat(fulfilled.fulfill("staff-1", NOW)).isTrue();
    assertThat(fulfilled.fulfill("staff-2", NOW.plusSeconds(60))).isFalse();
    assertThat(fulfilled.getFulfilledBy()).isEqualTo("staff-1");
    assertThatThrownBy(() -> fulfilled.cancel("staff-1", "Late", NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> placed(null).cancel("staff-1", " ", NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRegisteredBuyerNeedsBothCustomerAndSubject() {
    assertThatThrownBy(() -> new Buyer(UUID.randomUUID(), null, "Name", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Buyer(null, null, " ", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lineTotalsAreExactSnapshots() {
    var item =
        OrderItem.snapshot(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Game", "en", 3, 89000);
    assertThat(item.getLineTotalMinor()).isEqualTo(267000);
    assertThatThrownBy(
            () ->
                OrderItem.snapshot(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "G",
                    "en",
                    2,
                    Long.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }
}
