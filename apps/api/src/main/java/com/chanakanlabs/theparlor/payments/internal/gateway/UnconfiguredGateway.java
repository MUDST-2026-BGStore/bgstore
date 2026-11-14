package com.chanakanlabs.theparlor.payments.internal.gateway;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Active when no provider is selected, so an environment without one refuses payments instead of
 * silently approving them. Counter sales still work; they never reach a gateway.
 */
@Component
@ConditionalOnProperty(
    name = "theparlor.payments.gateway",
    havingValue = "none",
    matchIfMissing = true)
class UnconfiguredGateway implements PaymentGateway {
  @Override
  public String name() {
    return "none";
  }

  @Override
  public IssuedToken tokenize(Card card) {
    throw unavailable();
  }

  @Override
  public Authorization authorize(String token, long amountMinor, String payableReference) {
    throw unavailable();
  }

  @Override
  public void capture(String reference, long amountMinor) {
    throw unavailable();
  }

  @Override
  public void cancel(String reference) {
    throw unavailable();
  }

  @Override
  public PromptPayCharge createPromptPay(
      long amountMinor, String payableReference, Instant expiresAt) {
    throw unavailable();
  }

  @Override
  public boolean isPaid(String reference) {
    throw unavailable();
  }

  private static GatewayFailure unavailable() {
    return new GatewayFailure("Online payments are not configured");
  }
}
