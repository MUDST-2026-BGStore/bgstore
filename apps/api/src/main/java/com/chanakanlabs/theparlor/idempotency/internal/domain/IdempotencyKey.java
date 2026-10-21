package com.chanakanlabs.theparlor.idempotency.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {
  @EmbeddedId private IdempotencyKeyId id;

  @Column(name = "request_hash", nullable = false)
  @Getter(AccessLevel.NONE)
  private byte[] requestHash;

  @Column(name = "resource_id")
  private UUID resourceId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public byte[] getRequestHash() {
    return requestHash == null ? null : requestHash.clone();
  }
}
