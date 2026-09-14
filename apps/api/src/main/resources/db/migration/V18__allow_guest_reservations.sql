-- Staff can reserve for a walk-in client who has no BGStore account. The
-- reservation still retains the entered name and phone number as its snapshot.
ALTER TABLE reservation DROP CONSTRAINT fk_reservation_client;
ALTER TABLE reservation ALTER COLUMN client_subject DROP NOT NULL;
CREATE INDEX ix_reservation_registered_client
    ON reservation (client_subject, created_at DESC, id DESC)
    WHERE client_subject IS NOT NULL;
