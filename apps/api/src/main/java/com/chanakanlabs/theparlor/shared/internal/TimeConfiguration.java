package com.chanakanlabs.theparlor.shared.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The single server clock; inject it instead of calling Instant.now() so time is testable. */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
