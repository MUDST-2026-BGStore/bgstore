package com.chanakanlabs.bgstore.clients;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The profile store the client module reads and writes, over Spring Data JPA.
 *
 * <p>Kept as a class rather than exposing {@link ClientProfileJpaRepository} directly so callers
 * keep speaking in {@link ClientProfileData} instead of the entity.
 */
@Repository
class ClientProfileRepository {

  private final ClientProfileJpaRepository profiles;

  ClientProfileRepository(ClientProfileJpaRepository profiles) {
    this.profiles = profiles;
  }

  @Transactional
  void createIfAbsent(String subject) {
    if (!profiles.existsById(subject)) {
      profiles.save(new ClientProfileRecord(subject));
    }
  }

  @Transactional(readOnly = true)
  Optional<ClientProfileData> findBySubject(String subject) {
    return profiles.findById(subject).map(ClientProfileRecord::toData);
  }

  @Transactional
  ClientProfileData complete(String subject, String phoneE164) {
    var profile = profiles.findById(subject).orElseThrow();
    profile.complete(phoneE164);
    return profiles.save(profile).toData();
  }
}
