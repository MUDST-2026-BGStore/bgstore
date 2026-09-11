package com.chanakanlabs.bgstore.playsessions.spi;

import com.chanakanlabs.bgstore.playsessions.domain.ActiveSessionSnapshot;
import java.util.Optional;

/**
 * Future read adapter scoped to a server-authenticated subject's active party membership. It must
 * not expose another party's session. Billing estimates are supplied by the future billing
 * boundary.
 */
public interface ActiveSessionLookupPort {

  Optional<ActiveSessionSnapshot> findByParticipantSubject(String authenticatedSubject);
}
