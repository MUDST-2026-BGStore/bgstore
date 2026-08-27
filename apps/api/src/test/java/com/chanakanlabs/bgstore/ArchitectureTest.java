package com.chanakanlabs.bgstore;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

final class ArchitectureTest {

  @Test
  void modularMonolithBoundariesAreValid() {
    ApplicationModules.of(BgstoreApiApplication.class).verify();
  }
}
