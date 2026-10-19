-- The dedicated column was authoritative; preserve it over any stale JSON key.
UPDATE accounts
SET preferences = preferences || jsonb_build_object('preferredLocale', preferred_locale);

ALTER TABLE accounts DROP COLUMN preferred_locale;
ALTER TABLE accounts ALTER COLUMN preferences SET DEFAULT '{"preferredLocale":"th-TH"}'::jsonb;
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_preferred_locale CHECK (
  preferences ? 'preferredLocale'
  AND jsonb_typeof(preferences -> 'preferredLocale') = 'string'
  AND preferences ->> 'preferredLocale' IN ('th-TH', 'en-US')
);
