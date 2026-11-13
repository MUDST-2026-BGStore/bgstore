@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "contracts::http",
      "contracts::models",
      "account",
      "branches",
      "branches::spi",
      "customers",
      "idempotency",
      "identity",
      "inventory",
      "inventory::spi",
      "pantry",
      "payments",
      "shared"
    })
package com.chanakanlabs.theparlor.reservations;
