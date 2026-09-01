package com.chanakanlabs.bgstore.web;

import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Binds query parameters to the contract's enums by their wire value.
 *
 * <p>Spring's lenient converter matches a constant name, so it maps {@code card} to {@code CARD}
 * but cannot map a camel-case value such as {@code allCopiesOut}. Jackson already reads these
 * through {@code fromValue}; this makes query strings agree with request bodies.
 */
@Configuration(proxyBeanMethods = false)
class ContractEnumConverters implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, GameAvailability.class, GameAvailability::fromValue);
    registry.addConverter(String.class, GameCategory.class, GameCategory::fromValue);
    registry.addConverter(String.class, GameLifecycle.class, GameLifecycle::fromValue);
  }
}
