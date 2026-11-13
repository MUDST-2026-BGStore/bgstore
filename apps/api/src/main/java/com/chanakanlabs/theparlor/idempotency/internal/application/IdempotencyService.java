package com.chanakanlabs.theparlor.idempotency.internal.application;

import com.chanakanlabs.theparlor.idempotency.Idempotency;
import com.chanakanlabs.theparlor.idempotency.internal.persistence.IdempotencyKeyRepository;
import com.chanakanlabs.theparlor.shared.Sha256;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class IdempotencyService implements Idempotency {
  private final IdempotencyKeyRepository keys;

  IdempotencyService(IdempotencyKeyRepository keys) {
    this.keys = keys;
  }

  @Override
  public UUID once(Scope scope, UUID key, String fingerprint, Supplier<UUID> creation) {
    byte[] hash = Sha256.of(fingerprint);
    // Claim the key first. A concurrent claim of the same key blocks here on the primary key
    // until the other transaction ends, then inserts nothing if that transaction committed.
    boolean claimed = keys.claim(scope.subject(), scope.method(), scope.path(), key, hash) == 1;
    if (!claimed) {
      var existing =
          keys.find(scope.subject(), scope.method(), scope.path(), key)
              .orElseThrow(IdempotencyService::conflict);
      if (!MessageDigest.isEqual(existing.getRequestHash(), hash)
          || existing.getResourceId() == null) throw conflict();
      return existing.getResourceId();
    }
    UUID resourceId = java.util.Objects.requireNonNull(creation.get());
    keys.complete(scope.subject(), scope.method(), scope.path(), key, resourceId);
    return resourceId;
  }

  private static ResponseStatusException conflict() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "Idempotency-Key was already used for a different request");
  }
}
