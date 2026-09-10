CREATE TABLE identity_accounts (
  subject TEXT PRIMARY KEY,
  username TEXT NOT NULL,
  email TEXT NOT NULL,
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE client_profiles (
  subject TEXT PRIMARY KEY REFERENCES identity_accounts (subject) ON DELETE RESTRICT,
  phone_e164 VARCHAR(13),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT client_profiles_phone_e164_format
    CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+66[689][0-9]{8}$')
);
