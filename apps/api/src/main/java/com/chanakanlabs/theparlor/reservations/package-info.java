@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "contracts::http",
      "contracts::models",
      "account",
      "account::spi",
      "branches",
      "branches::spi",
      "catalog::spi",
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
