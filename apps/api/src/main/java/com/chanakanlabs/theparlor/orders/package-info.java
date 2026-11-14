@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "contracts::http",
      "contracts::models",
      "account",
      "account::spi",
      "branches",
      "catalog::spi",
      "customers",
      "idempotency",
      "identity",
      "inventory",
      "payments",
      "shared"
    })
package com.chanakanlabs.theparlor.orders;
