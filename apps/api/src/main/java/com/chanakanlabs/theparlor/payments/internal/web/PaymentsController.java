package com.chanakanlabs.theparlor.payments.internal.web;

import com.chanakanlabs.theparlor.contracts.api.PaymentsApi;
import com.chanakanlabs.theparlor.contracts.model.CreatePaymentTokenRequest;
import com.chanakanlabs.theparlor.contracts.model.OmiseWebhookEvent;
import com.chanakanlabs.theparlor.contracts.model.PaymentConfiguration;
import com.chanakanlabs.theparlor.contracts.model.PaymentConfiguration.CardTokenizationEnum;
import com.chanakanlabs.theparlor.contracts.model.PaymentToken;
import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.payments.internal.application.PaymentTokensService;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentsController implements PaymentsApi {
  private final PaymentTokensService tokens;
  private final PaymentGateway gateway;
  private final Payments payments;

  public PaymentsController(
      PaymentTokensService tokens, PaymentGateway gateway, Payments payments) {
    this.tokens = tokens;
    this.gateway = gateway;
    this.payments = payments;
  }

  @Override
  public PaymentToken createPaymentToken(CreatePaymentTokenRequest createPaymentTokenRequest) {
    return tokens.create(createPaymentTokenRequest);
  }

  @Override
  public PaymentConfiguration getPaymentConfiguration() {
    var key = gateway.browserTokenizationKey();
    if (key.isEmpty()) return new PaymentConfiguration(CardTokenizationEnum.SERVER);
    var configuration = new PaymentConfiguration(CardTokenizationEnum.OMISE);
    configuration.setOmisePublicKey(key.get());
    return configuration;
  }

  @Override
  public void receiveOmiseWebhook(OmiseWebhookEvent omiseWebhookEvent) {
    var data = omiseWebhookEvent.getData();
    // Only charge events matter; the charge's real state is fetched from the provider.
    if (data == null || !"charge".equals(data.getObject())) return;
    payments.gatewayChargeChanged(data.getId());
  }
}
