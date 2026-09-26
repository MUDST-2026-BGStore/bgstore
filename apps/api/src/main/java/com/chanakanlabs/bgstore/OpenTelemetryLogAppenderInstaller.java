package com.chanakanlabs.bgstore;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Installs the OpenTelemetry Logback appender with the auto-configured SDK so application logs are
 * exported over OTLP (see logback-spring.xml).
 */
@Component
class OpenTelemetryLogAppenderInstaller implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  OpenTelemetryLogAppenderInstaller(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterPropertiesSet() {
    OpenTelemetryAppender.install(openTelemetry);
  }
}
