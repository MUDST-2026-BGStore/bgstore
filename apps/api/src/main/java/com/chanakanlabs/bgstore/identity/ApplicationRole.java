package com.chanakanlabs.bgstore.identity;

import java.util.Optional;

public enum ApplicationRole {
  CLIENT,
  STAFF,
  MANAGER;

  static Optional<ApplicationRole> fromClaim(String value) {
    try {
      return Optional.of(valueOf(value));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }
}
