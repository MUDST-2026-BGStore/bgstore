package com.chanakanlabs.bgstore.identity;

/**
 * Keycloak-owned identity fields accepted by a future account-management API.
 *
 * <p>The BGStore database may keep a synchronized projection of these values, but Keycloak remains
 * their source of truth.
 */
public record AccountProfileUpdateCommand(
    String username, String firstName, String lastName, String email) {}
