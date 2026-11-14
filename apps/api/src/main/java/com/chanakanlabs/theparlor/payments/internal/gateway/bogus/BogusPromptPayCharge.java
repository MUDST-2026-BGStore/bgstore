package com.chanakanlabs.theparlor.payments.internal.gateway.bogus;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A PromptPay charge issued by the bogus gateway. It is paid only when paid_at is set. */
@Entity
@Table(name = "bogus_promptpay_charges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BogusPromptPayCharge {
  @Id
  @Column(nullable = false, updatable = false)
  private String reference;

  @Column(name = "amount_minor", nullable = false, updatable = false)
  private long amountMinor;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  static BogusPromptPayCharge issue(String reference, long amountMinor, Instant expiresAt) {
    var charge = new BogusPromptPayCharge();
    charge.reference = reference;
    charge.amountMinor = amountMinor;
    charge.expiresAt = expiresAt;
    return charge;
  }
}
