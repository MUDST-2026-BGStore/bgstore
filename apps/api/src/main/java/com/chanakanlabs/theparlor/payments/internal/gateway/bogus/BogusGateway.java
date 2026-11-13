package com.chanakanlabs.theparlor.payments.internal.gateway.bogus;

import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import com.chanakanlabs.theparlor.shared.Sha256;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A stand-in provider for development and tests. It moves no money and talks to nobody: every valid
 * card is approved, except the test numbers below, which force each failure a real provider can
 * produce.
 *
 * <ul>
 *   <li>4000 0000 0000 0002: declined at authorization
 *   <li>4000 0000 0000 9995: declined for insufficient funds at authorization
 *   <li>4000 0000 0000 0119: provider error at authorization
 *   <li>4000 0000 0000 0341: authorizes, then is declined at capture
 * </ul>
 *
 * Never enable it where real orders are taken.
 */
@Component
@ConditionalOnProperty(name = "theparlor.payments.gateway", havingValue = "bogus")
public class BogusGateway implements PaymentGateway {
  public static final String NAME = "bogus";

  static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
  private static final String CAPTURE_DECLINE_SUFFIX = "_capture_declined";

  enum Behavior {
    APPROVE,
    DECLINE,
    INSUFFICIENT_FUNDS,
    AUTHORIZE_ERROR,
    CAPTURE_DECLINE
  }

  private static final Map<String, Behavior> TEST_CARDS =
      Map.of(
          "4000000000000002", Behavior.DECLINE,
          "4000000000009995", Behavior.INSUFFICIENT_FUNDS,
          "4000000000000119", Behavior.AUTHORIZE_ERROR,
          "4000000000000341", Behavior.CAPTURE_DECLINE);

  private final BogusPaymentTokenRepository tokens;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  BogusGateway(BogusPaymentTokenRepository tokens, Clock clock) {
    this.tokens = tokens;
    this.clock = clock;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  @Transactional
  public IssuedToken tokenize(Card card) {
    byte[] secret = new byte[32];
    random.nextBytes(secret);
    String token = "bogus_tok_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    var expiresAt = clock.instant().plus(TOKEN_LIFETIME);
    tokens.save(
        BogusPaymentToken.issue(
            Sha256.of(token),
            card.brand(),
            card.last4(),
            TEST_CARDS.getOrDefault(card.digits(), Behavior.APPROVE),
            expiresAt));
    return new IssuedToken(token, card.brand(), card.last4(), expiresAt);
  }

  @Override
  @Transactional
  public Authorization authorize(String token, long amountMinor, String payableReference) {
    var now = clock.instant();
    var stored =
        tokens
            .lockByHash(Sha256.of(token))
            .filter(candidate -> candidate.usableAt(now))
            .orElseThrow(
                () -> new GatewayDeclined("Payment token is unknown, expired or already used"));
    stored.consume(now);
    String reference = "bogus_auth_" + UUID.randomUUID();
    switch (stored.getBehavior()) {
      case DECLINE -> throw new GatewayDeclined("The card was declined");
      case INSUFFICIENT_FUNDS -> throw new GatewayDeclined("The card has insufficient funds");
      case AUTHORIZE_ERROR -> throw new GatewayFailure("The payment provider failed");
      case CAPTURE_DECLINE -> reference += CAPTURE_DECLINE_SUFFIX;
      case APPROVE -> {}
    }
    return new Authorization(reference, stored.getCardBrand(), stored.getCardLast4());
  }

  @Override
  public void capture(String reference, long amountMinor) {
    if (reference.endsWith(CAPTURE_DECLINE_SUFFIX))
      throw new GatewayDeclined("The card was declined at capture");
  }

  @Override
  public void cancel(String reference) {
    // Nothing was ever reserved, so there is nothing to release.
  }
}
