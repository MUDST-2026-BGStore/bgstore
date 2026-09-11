ALTER TABLE table_reservation
    ADD COLUMN reservation_id VARCHAR(64);

ALTER TABLE table_reservation
    ADD CONSTRAINT fk_table_reservation_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE CASCADE;

CREATE INDEX ix_table_reservation_reservation
    ON table_reservation (reservation_id);
