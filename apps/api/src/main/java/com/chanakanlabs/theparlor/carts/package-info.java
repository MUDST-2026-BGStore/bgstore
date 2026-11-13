@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "contracts::http",
      "contracts::models",
      "account",
      "branches",
      "idempotency",
      "identity",
      "inventory",
      "orders",
      "shared"
    })
package com.chanakanlabs.theparlor.carts;
