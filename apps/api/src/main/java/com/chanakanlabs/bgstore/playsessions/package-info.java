/**
 * In-store play-session module scaffold for the active-session screen.
 *
 * <p>No application service, HTTP endpoint, or persistence adapter is registered yet. Introduce the
 * OpenAPI contract before implementing transport adapters. Billing owns fee calculation;
 * operational session closure requires staff-or-manager authorization and a confirmed final fee or
 * waiver.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Play sessions",
    allowedDependencies = {"identity"})
package com.chanakanlabs.bgstore.playsessions;
