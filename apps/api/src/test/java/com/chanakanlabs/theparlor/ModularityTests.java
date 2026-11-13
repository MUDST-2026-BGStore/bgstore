package com.chanakanlabs.theparlor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {
  @Test
  void modulesHaveNoCyclesOrIllegalAccessToInternals() {
    var modules = ApplicationModules.of(TheparlorApiApplication.class);
    modules.verify();
    assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
        .containsExactlyInAnyOrder(
            "account",
            "branches",
            "carts",
            "catalog",
            "contracts",
            "customers",
            "idempotency",
            "identity",
            "inventory",
            "orders",
            "pantry",
            "payments",
            "rentals",
            "reservations",
            "shared",
            "wiring");
  }
}
