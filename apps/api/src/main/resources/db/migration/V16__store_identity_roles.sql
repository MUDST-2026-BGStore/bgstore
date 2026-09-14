ALTER TABLE identity_accounts ADD COLUMN application_role VARCHAR(16) NOT NULL DEFAULT 'CLIENT';
ALTER TABLE identity_accounts ADD CONSTRAINT ck_identity_application_role
    CHECK (application_role IN ('CLIENT', 'STAFF', 'MANAGER'));
