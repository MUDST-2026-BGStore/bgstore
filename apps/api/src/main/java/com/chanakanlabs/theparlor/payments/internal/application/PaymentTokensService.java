package com.chanakanlabs.theparlor.payments.internal.application;

import com.chanakanlabs.theparlor.contracts.model.CardBrand;
import com.chanakanlabs.theparlor.contracts.model.CreatePaymentTokenRequest;
import com.chanakanlabs.theparlor.contracts.model.PaymentToken;
import com.chanakanlabs.theparlor.payments.internal.domain.CardRules;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/** Validates a card locally, then lets the gateway turn it into a single-use token. */
@Service
public class PaymentTokensService {
  private final PaymentGateway gateway;
  private final Clock clock;

  public PaymentTokensService(PaymentGateway gateway, Clock clock) {
    this.gateway = gateway;
    this.clock = clock;
  }

  public PaymentToken create(CreatePaymentTokenRequest request) {
    if (request.getHolderName() == null || request.getHolderName().isBlank())
      throw new IllegalArgumentException("Cardholder name is required");
    String digits = CardRules.digits(request.getNumber());
    // A card is good through the last day of its expiry month anywhere on Earth.
    var month = YearMonth.now(clock.withZone(ZoneOffset.ofHours(-12)));
    var brand =
        CardRules.validate(
            digits, request.getExpMonth(), request.getExpYear(), request.getCvc(), month);
    var card =
        new PaymentGateway.Card(
            digits, brand, request.getExpMonth(), request.getExpYear(), request.getCvc());
    var issued = PaymentsService.calling(() -> gateway.tokenize(card));
    return new PaymentToken(
        issued.token(),
        CardBrand.valueOf(issued.brand().name()),
        issued.last4(),
        issued.expiresAt().atOffset(ZoneOffset.UTC));
  }
}
