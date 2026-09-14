package com.chanakanlabs.bgstore.identity;

import java.util.Set;

public record AuthenticatedIdentity(
    String subject,
    String username,
    String email,
    String firstName,
    String lastName,
    Set<ApplicationRole> roles,
    Set<String> branchScope) {

  public AuthenticatedIdentity(
      String subject,
      String username,
      String email,
      String firstName,
      String lastName,
      Set<ApplicationRole> roles) {
    this(subject, username, email, firstName, lastName, roles, Set.of());
  }

  public boolean isClientOnly() {
    return roles.contains(ApplicationRole.CLIENT)
        && !roles.contains(ApplicationRole.STAFF)
        && !roles.contains(ApplicationRole.MANAGER);
  }
}
