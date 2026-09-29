package com.chanakanlabs.bgstore.identity;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The registered client account lookups the identity module publishes to other modules.
 *
 * <p>Modules needing client account facts depend on this port instead of identity's persistence
 * mapping, so the boundary stays enforceable by the architecture tests.
 */
public interface ClientDirectory {

  /** A registered client as the directory presents it. */
  record ClientLookup(
      String subject,
      String displayName,
      @Nullable String firstName,
      @Nullable String lastName,
      @Nullable String phone) {}

  /** Whether the subject belongs to a registered client account. */
  boolean existsClient(String subject);

  /** Registered clients matching a username, name, or phone search; at most 50 entries. */
  List<ClientLookup> findClients(@Nullable String search);
}
