package com.chanakanlabs.bgstore.identity;

/** Boundary for a future Keycloak password-update adapter. */
public interface AccountPasswordUpdatePort {

  void updatePassword(String subject, char[] newPassword);
}
