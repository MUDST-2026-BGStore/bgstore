@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "contracts::http",
      "contracts::models",
      "branches",
      "identity",
      "idempotency",
      "pantry",
      "inventory::spi",
      "reservations",
      "reservations::spi",
      "shared"
    })
package com.chanakanlabs.theparlor.rentals;
