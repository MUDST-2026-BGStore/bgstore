package com.chanakanlabs.bgstore.playsessions.application;

import com.chanakanlabs.bgstore.playsessions.domain.ActiveSessionSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * Future inbound boundary for the authenticated client's current in-store session.
 *
 * <p>Implementations must derive the subject from CurrentIdentityProvider, enforce completed
 * onboarding, and verify active party membership for every operation. Browser-supplied subjects are
 * never accepted. Empty means no active session; infrastructure failures must remain errors.
 *
 * <p>Client actions only request staff assistance. They never close a session, stop billing, or
 * confirm a final fee. Operational writes require AccessPolicy.requireStaffOrManager().
 */
public interface CurrentPlaySessionUseCase {

  Optional<ActiveSessionSnapshot> findCurrentSession();

  UUID requestStaffAssistance(UUID sessionId, UUID requestId);

  UUID requestEndPlaying(UUID sessionId, UUID requestId);
}
