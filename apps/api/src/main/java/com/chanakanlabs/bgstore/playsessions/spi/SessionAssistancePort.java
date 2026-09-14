package com.chanakanlabs.bgstore.playsessions.spi;

import java.util.UUID;

/**
 * Future durable staff-request adapter, not an operational session-closing adapter.
 *
 * <p>Atomically verify that the session is still active and the authenticated subject is a party
 * member before recording a request. Deduplicate retries by subject, session, kind, and request ID;
 * return the original receipt ID. Delivery and audit details remain to be implemented.
 */
public interface SessionAssistancePort {

  UUID recordRequest(UUID sessionId, String authenticatedSubject, RequestKind kind, UUID requestId);

  enum RequestKind {
    CALL_STAFF,
    END_PLAYING
  }
}
