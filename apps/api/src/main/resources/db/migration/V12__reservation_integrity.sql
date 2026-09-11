ALTER TABLE reservation
    ADD CONSTRAINT fk_reservation_client
        FOREIGN KEY (client_subject) REFERENCES identity_accounts (subject) ON DELETE RESTRICT;

ALTER TABLE reservation
    ADD CONSTRAINT fk_reservation_table
        FOREIGN KEY (table_id) REFERENCES store_table (id) ON DELETE RESTRICT;

CREATE INDEX ix_reservation_client_created
    ON reservation (client_subject, created_at DESC, id DESC);
