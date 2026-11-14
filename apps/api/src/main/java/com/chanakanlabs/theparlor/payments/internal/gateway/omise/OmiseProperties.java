package com.chanakanlabs.theparlor.payments.internal.gateway.omise;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Omise (Opn Payments) settings, bound from theparlor.payments.omise. The keys come from the
 * environment and are never logged. The URL and timeouts only change for tests.
 */
@ConfigurationProperties("theparlor.payments.omise")
record OmiseProperties(
    String publicKey,
    String secretKey,
    @DefaultValue("https://api.omise.co") URI apiUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("20s") Duration readTimeout) {
  @Override
  public String toString() {
    return "OmiseProperties[apiUrl=" + apiUrl + "]";
  }
}
