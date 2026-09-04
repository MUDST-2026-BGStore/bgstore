package com.chanakanlabs.bgstore.clients;

import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Database adapter reserved for the future profile-image API. */
@Repository
class ClientProfileImageRepository {

  private final DSLContext database;

  ClientProfileImageRepository(DSLContext database) {
    this.database = database;
  }

  Optional<ClientProfileImageData> findBySubject(String subject) {
    return Optional.ofNullable(
            database.fetchOne(
                """
                SELECT profile_image_media_type, profile_image_filename, profile_image
                FROM client_profiles
                WHERE subject = ? AND profile_image IS NOT NULL
                """,
                subject))
        .map(
            record ->
                new ClientProfileImageData(
                    record.get("profile_image_media_type", String.class),
                    record.get("profile_image_filename", String.class),
                    record.get("profile_image", byte[].class)));
  }

  void save(String subject, ProfileImageUpload image) {
    database.execute(
        """
        UPDATE client_profiles
        SET profile_image = ?,
            profile_image_media_type = ?,
            profile_image_filename = ?,
            profile_image_updated_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE subject = ?
        """,
        image.content(),
        image.mediaType(),
        image.filename(),
        subject);
  }
}
