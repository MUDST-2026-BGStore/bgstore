ALTER TABLE reservation
    DROP CONSTRAINT ck_reservation_status;

ALTER TABLE reservation
    ADD CONSTRAINT ck_reservation_status
        CHECK (status IN ('Reserved', 'CheckedIn', 'Completed', 'Cancelled'));
