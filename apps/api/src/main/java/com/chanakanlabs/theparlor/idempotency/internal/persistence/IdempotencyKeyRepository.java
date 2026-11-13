package com.chanakanlabs.theparlor.idempotency.internal.persistence;

import com.chanakanlabs.theparlor.idempotency.internal.domain.IdempotencyKey;
import com.chanakanlabs.theparlor.idempotency.internal.domain.IdempotencyKeyId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {
  /** Returns 1 when this transaction now owns the key, 0 when it already existed. */
  @Modifying
  @Query(
      value =
          "INSERT INTO idempotency_keys (subject, http_method, path, idempotency_key, request_hash)"
              + " VALUES (:subject, :method, :path, :key, :hash) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int claim(String subject, String method, String path, UUID key, byte[] hash);

  @Query(
      "select k from IdempotencyKey k where k.id.subject = :subject and k.id.httpMethod = :method"
          + " and k.id.path = :path and k.id.idempotencyKey = :key")
  Optional<IdempotencyKey> find(String subject, String method, String path, UUID key);

  @Modifying
  @Query(
      "update IdempotencyKey k set k.resourceId = :resourceId where k.id.subject = :subject"
          + " and k.id.httpMethod = :method and k.id.path = :path and k.id.idempotencyKey = :key")
  void complete(String subject, String method, String path, UUID key, UUID resourceId);
}
