package com.chanakanlabs.bgstore.clients;

import org.springframework.lang.Nullable;

/** A phone of {@code null} is a profile that has not been completed yet. */
public record ClientProfileData(@Nullable String phone, boolean completed) {}
