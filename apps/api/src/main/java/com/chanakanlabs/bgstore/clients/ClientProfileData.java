package com.chanakanlabs.bgstore.clients;

import org.jspecify.annotations.Nullable;

/** Nullable profile fields indicate a client who has not completed onboarding yet. */
public record ClientProfileData(
    @Nullable String phone,
    boolean completed,
    @Nullable String firstName,
    @Nullable String lastName) {

  public ClientProfileData(@Nullable String phone, boolean completed) {
    this(phone, completed, null, null);
  }
}
