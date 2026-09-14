package com.chanakanlabs.bgstore.playsessions.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * THB billing projection, not a final checkout amount. A future billing adapter must use the
 * pricing policy version captured when play started. Refreshing an estimate must not switch policy
 * versions.
 */
public record SessionFeeEstimate(
    BigDecimal hourlyRatePerPerson,
    BigDecimal accumulatedFee,
    String pricingPolicyVersion,
    Instant calculatedAt) {

  public String currency() {
    return "THB";
  }
}
