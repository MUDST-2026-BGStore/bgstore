package com.chanakanlabs.bgstore.identity;

/**
 * Boundary for a future Keycloak adapter. Password changes deliberately use a separate port and
 * must not be added to the general profile command.
 */
public interface AccountProfileUpdatePort {

  void updateProfile(String subject, AccountProfileUpdateCommand update);
}
