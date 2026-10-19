package com.chanakanlabs.theparlor.identity;

import java.util.List;

/** Identity established during OIDC login. Branch assignments are separate application data. */
public record UserProfile(
    String issuer,
    String subject,
    String username,
    String displayName,
    String email,
    boolean emailVerified,
    List<String> roles) {
  public UserProfile {
    roles = List.copyOf(roles);
  }
}
