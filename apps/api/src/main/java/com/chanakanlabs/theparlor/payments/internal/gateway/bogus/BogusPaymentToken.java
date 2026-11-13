package com.chanakanlabs.theparlor.payments.internal.gateway.bogus;

import com.chanakanlabs.theparlor.payments.internal.domain.CardBrand;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** What the bogus gateway remembers about a tokenized card: how it behaves, never the card. */
@Entity
@Table(name = "bogus_payment_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BogusPaymentToken {
  @Id
  @Column(name = "token_hash", nullable = false, updatable = false)
  @Getter(AccessLevel.NONE)
  private byte[] tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "card_brand", nullable = false, updatable = false, length = 20)
  private CardBrand cardBrand;

  @Column(name = "card_last4", nullable = false, updatable = false, length = 4)
  private String cardLast4;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 30)
  private BogusGateway.Behavior behavior;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  static BogusPaymentToken issue(
      byte[] tokenHash,
      CardBrand brand,
      String last4,
      BogusGateway.Behavior behavior,
      Instant expiresAt) {
    var token = new BogusPaymentToken();
    token.tokenHash = tokenHash.clone();
    token.cardBrand = brand;
    token.cardLast4 = last4;
    token.behavior = behavior;
    token.expiresAt = expiresAt;
    return token;
  }

  boolean usableAt(Instant now) {
    return consumedAt == null && now.isBefore(expiresAt);
  }

  void consume(Instant now) {
    consumedAt = now;
  }
}
