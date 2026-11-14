package com.chanakanlabs.theparlor.payments.internal.gateway.omise;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects Omise as the payment provider when theparlor.payments.gateway=omise. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "theparlor.payments.gateway", havingValue = "omise")
@EnableConfigurationProperties(OmiseProperties.class)
class OmiseConfiguration {
  @Bean
  OmiseGateway omiseGateway(OmiseProperties properties) {
    return new OmiseGateway(properties);
  }
}
