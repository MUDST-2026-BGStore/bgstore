package com.chanakanlabs.theparlor.idempotency.internal.persistence;

import com.chanakanlabs.theparlor.idempotency.internal.domain.IdempotencyKey;
import com.chanakanlabs.theparlor.idempotency.internal.domain.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {}
