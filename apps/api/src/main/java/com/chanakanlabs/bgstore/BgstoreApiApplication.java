package com.chanakanlabs.bgstore;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BgstoreApiApplication {

  @Bean
  Clock applicationClock() {
    return Clock.systemUTC();
  }

  public static void main(String[] args) {
    SpringApplication.run(BgstoreApiApplication.class, args);
  }
}
