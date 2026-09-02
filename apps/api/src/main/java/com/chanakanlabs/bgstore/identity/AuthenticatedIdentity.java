package com.chanakanlabs.bgstore.identity;

import java.util.Set;

public record AuthenticatedIdentity(
    String subject,
    String username,
    String email,
    String firstName,
    String lastName,
    Set<ApplicationRole> roles) {

  public boolean isClientOnly() {
    return roles.contains(ApplicationRole.CLIENT)
        && !roles.contains(ApplicationRole.STAFF)
        && !roles.contains(ApplicationRole.MANAGER);
  }
}
