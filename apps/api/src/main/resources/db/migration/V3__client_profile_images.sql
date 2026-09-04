ALTER TABLE client_profiles
  ADD COLUMN profile_image BYTEA,
  ADD COLUMN profile_image_media_type VARCHAR(64),
  ADD COLUMN profile_image_filename VARCHAR(255),
  ADD COLUMN profile_image_updated_at TIMESTAMPTZ,
  ADD CONSTRAINT client_profiles_profile_image_size
    CHECK (profile_image IS NULL OR OCTET_LENGTH(profile_image) <= 5242880),
  ADD CONSTRAINT client_profiles_profile_image_media_type
    CHECK (
      profile_image_media_type IS NULL
      OR profile_image_media_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
  ADD CONSTRAINT client_profiles_profile_image_fields
    CHECK (
      (
        profile_image IS NULL
        AND profile_image_media_type IS NULL
        AND profile_image_filename IS NULL
        AND profile_image_updated_at IS NULL
      )
      OR (
        profile_image IS NOT NULL
        AND profile_image_media_type IS NOT NULL
        AND profile_image_filename IS NOT NULL
        AND profile_image_updated_at IS NOT NULL
      )
    );
