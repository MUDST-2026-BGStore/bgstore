package com.chanakanlabs.theparlor.rentals.internal.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes active rentals once their booked end passes, so a table does not stay occupied (and copies
 * stay committed) until someone remembers to check out. Each rental closes in its own transaction
 * under a row lock, so replicas and staff checkout cannot double-bill.
 */
@Component
@ConditionalOnProperty(
    name = "theparlor.rentals.auto-close.enabled",
    havingValue = "true",
    matchIfMissing = true)
class RentalAutoCloser {
  private static final Logger log = LoggerFactory.getLogger(RentalAutoCloser.class);
  private final RentalsService rentals;

  RentalAutoCloser(RentalsService rentals) {
    this.rentals = rentals;
  }

  @Scheduled(fixedDelayString = "${theparlor.rentals.auto-close.interval:PT10S}")
  void closeExpired() {
    for (UUID id : rentals.expiredActiveIds()) {
      try {
        rentals.autoClose(id);
      } catch (RuntimeException failure) {
        // One bad rental must not stop the rest; it is retried on the next run.
        log.warn("Automatic closure of rental {} failed", id, failure);
      }
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "theparlor.rentals.auto-close.enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class Scheduling {}
}
