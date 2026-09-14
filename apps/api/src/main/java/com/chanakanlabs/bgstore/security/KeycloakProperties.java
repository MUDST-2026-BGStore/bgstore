package com.chanakanlabs.bgstore.security;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration needed to build browser-facing Keycloak URLs. */
@ConfigurationProperties(prefix = "bgstore.security.keycloak")
@Validated
record KeycloakProperties(@NotNull URI publicUrl) {}
