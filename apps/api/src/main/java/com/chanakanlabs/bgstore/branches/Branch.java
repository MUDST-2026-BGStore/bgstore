package com.chanakanlabs.bgstore.branches;

import java.util.UUID;

/**
 * A store other modules can stock or schedule against.
 *
 * <p>The design and the UI call these branches; {@code docs/domain-model.md} calls the same thing a
 * location.
 */
public record Branch(UUID id, String name) {}
