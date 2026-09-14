-- V4 established international E.164 validation. Widen the storage column to
-- hold the full 15-digit E.164 payload plus its leading plus sign.
ALTER TABLE client_profiles
  ALTER COLUMN phone_e164 TYPE VARCHAR(16);
