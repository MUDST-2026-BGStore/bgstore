package com.chanakanlabs.theparlor.payments.internal.gateway.bogus;

import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import com.chanakanlabs.theparlor.shared.Sha256;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * <p>PromptPay charges are never paid on their own. Set bogus_promptpay_charges.paid_at to simulate
 * the customer paying; the next refresh or provider notice then captures the payment.
 *
 * <p>Never enable it where real orders are taken.
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
  private final BogusPromptPayChargeRepository promptPay;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  BogusGateway(
      BogusPaymentTokenRepository tokens, BogusPromptPayChargeRepository promptPay, Clock clock) {
    this.tokens = tokens;
    this.promptPay = promptPay;
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

  @Override
  @Transactional
  public PromptPayCharge createPromptPay(
      long amountMinor, String payableReference, Instant expiresAt) {
    String reference = "bogus_promptpay_" + UUID.randomUUID();
    promptPay.save(BogusPromptPayCharge.issue(reference, amountMinor, expiresAt));
    return new PromptPayCharge(reference, placeholderQr(amountMinor), expiresAt);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isPaid(String reference) {
    return promptPay
        .findById(reference)
        .map(charge -> charge.getPaidAt() != null)
        .orElseThrow(() -> new GatewayFailure("Unknown PromptPay charge"));
  }

  /** A clearly fake QR image, so nobody mistakes a development charge for a real one. */
  private static String placeholderQr(long amountMinor) {
    String amount = String.format("%d.%02d", amountMinor / 100, amountMinor % 100);
    String svg =
        "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 200'>"
            + "<rect width='200' height='200' fill='white'/>"
            + "<rect x='10' y='10' width='180' height='180' fill='none' stroke='black'"
            + " stroke-width='4' stroke-dasharray='12 8'/>"
            + "<text x='100' y='90' font-family='sans-serif' font-size='16' text-anchor='middle'>"
            + "Development QR</text>"
            + "<text x='100' y='120' font-family='sans-serif' font-size='14' text-anchor='middle'>"
            + "THB "
            + amount
            + "</text></svg>";
    return "data:image/svg+xml;base64,"
        + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
  }
}
