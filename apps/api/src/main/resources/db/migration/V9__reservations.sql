CREATE TABLE reservation (
    id VARCHAR(64) PRIMARY KEY,
    client_subject VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    reservation_date VARCHAR(64) NOT NULL,
    time_slot VARCHAR(64) NOT NULL,
    party_size INTEGER NOT NULL,
    table_id BIGINT NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    seats INTEGER NOT NULL,
    rate_per_hour INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(64) NOT NULL,
    check_in_time VARCHAR(64) NOT NULL DEFAULT '-',
    actual_check_out VARCHAR(64) NOT NULL DEFAULT '-',
    overtime_minutes INTEGER NOT NULL DEFAULT 0,
    total_price INTEGER NOT NULL DEFAULT 0,
    can_cancel BOOLEAN NOT NULL DEFAULT true,
    thumbnail_url VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reservation_status CHECK (status IN ('Reserved', 'Completed', 'Cancelled'))
);

CREATE INDEX ix_reservation_client ON reservation (client_subject);
CREATE INDEX ix_reservation_status ON reservation (status);
