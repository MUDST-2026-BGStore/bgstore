package com.chanakanlabs.bgstore.billing;

import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * An operational record of a confirmed fee settled through a payment gateway.
 *
 * <p>Only successful charges are stored: a declined charge aborts check-out and leaves the session
 * open for a retry. Waived fees are not recorded because they close the session without charging.
 * For card charges only the detected brand and the number's last four digits are stored — never the
 * full card number, its security code or its expiry.
 */
@Entity
@Table(name = "payment")
@SuppressWarnings("NullAway.Init")
public class PaymentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reservation_id", nullable = false, length = 64)
  private String reservationId;

  @Column(name = "amount", nullable = false)
  private int amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "method", nullable = false, length = 32)
  private String method;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "gateway", nullable = false, length = 32)
  private String gateway;

  @Column(name = "gateway_reference", nullable = false, length = 64)
  private String gatewayReference;

  @Nullable
  @Column(name = "card_brand", length = 32)
  private String cardBrand;

  @Nullable
  @Column(name = "card_last4", length = 4)
  private String cardLast4;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected PaymentEntity() {}

  PaymentEntity(
      String reservationId,
      int amount,
      String currency,
      PaymentMethod method,
      String gateway,
      String gatewayReference,
      @Nullable String cardBrand,
      @Nullable String cardLast4) {
    this.reservationId = reservationId;
    this.amount = amount;
    this.currency = currency;
    this.method = method.getValue();
    this.status = "Succeeded";
    this.gateway = gateway;
    this.gatewayReference = gatewayReference;
    this.cardBrand = cardBrand;
    this.cardLast4 = cardLast4;
  }

  String gateway() {
    return gateway;
  }

  String gatewayReference() {
    return gatewayReference;
  }

  @Nullable String cardBrand() {
    return cardBrand;
  }

  @Nullable String cardLast4() {
    return cardLast4;
  }
}
