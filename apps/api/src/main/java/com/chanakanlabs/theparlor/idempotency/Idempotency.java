package com.chanakanlabs.theparlor.idempotency;

import java.util.UUID;
import java.util.function.Supplier;

/** Module interface that makes a resource-creating request safe to retry. */
public interface Idempotency {
  /**
   * Runs the creation at most once per scope and key, inside the caller's transaction, and returns
   * the ID of the resource it created. A retry with the same fingerprint returns the original ID
   * without running it again; a different fingerprint is a 409. If the creation throws, the
   * transaction rolls back and the key is free to retry.
   *
   * <p>Two concurrent requests with one key are serialized by the key's row: the second waits for
   * the first to commit and then replays its result.
   */
  UUID once(Scope scope, UUID key, String fingerprint, Supplier<UUID> creation);

  /**
   * Who is asking and for what. subject is the authenticated subject, or another stable identifier
   * of the caller when there is none; path is the concrete request path.
   */
  record Scope(String subject, String method, String path) {}
}
