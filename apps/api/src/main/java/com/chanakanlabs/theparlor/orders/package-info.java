@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "contracts::http",
      "contracts::models",
      "account",
      "branches",
      "customers",
      "idempotency",
      "identity",
      "inventory",
      "payments",
      "shared"
    })
package com.chanakanlabs.theparlor.orders;
