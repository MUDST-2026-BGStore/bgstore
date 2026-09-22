package com.chanakanlabs.bgstore.reservations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionAssistanceRequestJpaRepository
    extends JpaRepository<SessionAssistanceRequestEntity, Long> {

  Optional<SessionAssistanceRequestEntity> findByRequestId(UUID requestId);
}
