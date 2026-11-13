package com.chanakanlabs.theparlor.payments.internal.gateway;

import com.chanakanlabs.theparlor.payments.internal.domain.CardBrand;
import java.time.Instant;

/**
 * The port every payment provider adapter implements. Exactly one adapter is active, chosen by
 * theparlor.payments.gateway. Adapters report a refusal by the card issuer as GatewayDeclined and
 * anything else that goes wrong as GatewayFailure; they never return a failed result.
 */
public interface PaymentGateway {
  /** Stable name stored with each payment, so later captures and voids reach the same provider. */
  String name();

  /** Exchanges card details for a single-use token. The card has already passed CardRules. */
  IssuedToken tokenize(Card card);

  /** Reserves the amount on the token's card and returns the provider's reference to it. */
  Authorization authorize(String token, long amountMinor, String payableReference);

  /** Takes the previously authorized amount. */
  void capture(String reference, long amountMinor);

  /** Releases an authorization that was never captured. */
  void cancel(String reference);

  /** Card details in transit to the provider. Never persisted; toString hides everything. */
  record Card(String digits, CardBrand brand, int expMonth, int expYear, String cvc) {
    @Override
    public String toString() {
      return "Card[" + brand + " ending " + last4() + "]";
    }

    public String last4() {
      return digits.substring(digits.length() - 4);
    }
  }

  record IssuedToken(String token, CardBrand brand, String last4, Instant expiresAt) {}

  record Authorization(String reference, CardBrand brand, String last4) {}

  /** The issuer refused, or the token is unknown, expired or used. The customer can retry. */
  class GatewayDeclined extends RuntimeException {
    public GatewayDeclined(String message) {
      super(message);
    }
  }

  /** The provider is unreachable, misbehaving or not configured. */
  class GatewayFailure extends RuntimeException {
    public GatewayFailure(String message) {
      super(message);
    }
  }
}
