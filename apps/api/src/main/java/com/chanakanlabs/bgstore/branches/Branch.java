package com.chanakanlabs.bgstore.branches;

import java.time.LocalTime;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * A store other modules can stock or schedule against.
 *
 * <p>The design and the UI call these branches; {@code docs/domain-model.md} calls the same thing a
 * location.
 *
 * @param address the postal address guests are shown; null until the store records one
 * @param opensAt Bangkok local time; null exactly when {@code closesAt} is
 */
public record Branch(
    UUID id,
    String name,
    @Nullable String address,
    @Nullable LocalTime opensAt,
    @Nullable LocalTime closesAt) {}
