package com.chanakanlabs.bgstore.clients;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class ClientProfileRepository {

  private final DSLContext database;

  ClientProfileRepository(DSLContext database) {
    this.database = database;
  }

  void createIfAbsent(String subject) {
    database.execute(
        "INSERT INTO client_profiles (subject) VALUES (?) ON CONFLICT (subject) DO NOTHING",
        subject);
  }

  Optional<ClientProfileData> findBySubject(String subject) {
    return Optional.ofNullable(
            database.fetchOne(
                "SELECT phone_e164, completed_at FROM client_profiles WHERE subject = ?", subject))
        .map(
            record ->
                new ClientProfileData(
                    record.get("phone_e164", String.class),
                    record.get("completed_at", OffsetDateTime.class) != null));
  }

  ClientProfileData complete(String subject, String phoneE164) {
    database.execute(
        """
        UPDATE client_profiles
        SET phone_e164 = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
        WHERE subject = ?
        """,
        phoneE164,
        subject);
    return findBySubject(subject).orElseThrow();
  }
}
