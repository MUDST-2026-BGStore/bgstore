package com.chanakanlabs.theparlor.orders.internal.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute, cancels orders whose PromptPay QR went unpaid past its deadline and releases their
 * stock. A payment that arrived late is recorded instead. Each order is handled in its own
 * transaction, so one failure does not hold up the rest.
 */
@Component
public class UnpaidOrderExpiry {
  private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiry.class);

  private final OrdersService orders;

  UnpaidOrderExpiry(OrdersService orders) {
    this.orders = orders;
  }

  /** Returns how many orders were cancelled. */
  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
  public int expireUnpaidOrders() {
    int cancelled = 0;
    for (UUID orderId : orders.unpaidPastDeadline()) {
      try {
        if (orders.expireUnpaidOrder(orderId)) cancelled++;
      } catch (RuntimeException failure) {
        // Most likely the provider is unreachable; the next run tries again.
        log.warn("Could not expire unpaid order {}: {}", orderId, failure.getMessage());
      }
    }
    return cancelled;
  }
}
