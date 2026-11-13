package com.chanakanlabs.theparlor.payments.internal.web;

import com.chanakanlabs.theparlor.contracts.api.PaymentsApi;
import com.chanakanlabs.theparlor.contracts.model.CreatePaymentTokenRequest;
import com.chanakanlabs.theparlor.contracts.model.PaymentToken;
import com.chanakanlabs.theparlor.payments.internal.application.PaymentTokensService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentsController implements PaymentsApi {
  private final PaymentTokensService tokens;

  public PaymentsController(PaymentTokensService tokens) {
    this.tokens = tokens;
  }

  @Override
  public PaymentToken createPaymentToken(CreatePaymentTokenRequest createPaymentTokenRequest) {
    return tokens.create(createPaymentTokenRequest);
  }
}
