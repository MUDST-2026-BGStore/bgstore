ALTER TABLE client_profiles
  DROP CONSTRAINT client_profiles_phone_e164_format;

ALTER TABLE client_profiles
  ADD CONSTRAINT client_profiles_phone_e164_format
  CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9][0-9]{7,14}$');
