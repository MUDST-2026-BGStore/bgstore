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

INSERT INTO reservation (
    id, client_subject, title, reservation_date, time_slot, party_size,
    table_id, table_name, seats, rate_per_hour, status, customer_name,
    phone_number, check_in_time, actual_check_out, overtime_minutes,
    total_price, can_cancel, thumbnail_url
) VALUES
    ('res-1', 'seed-client', 'Catan Evening', '13/09/2024', '18:00 - 20:00', 4, 5, 'Table 5', 4, 20, 'Reserved', 'John Doe', '0123456789', '-', '-', 0, 40, true, '/images/table-sample.png'),
    ('res-2', 'seed-client', 'D&D Campaign Session', '15/09/2024', '14:00 - 18:00', 6, 2, 'Table 2', 6, 25, 'Reserved', 'John Doe', '0123456789', '-', '-', 0, 100, true, '/images/table-sample.png'),
    ('res-3', 'seed-client', 'Ticket to Ride Tournament', '10/09/2024', '19:00 - 21:00', 4, 8, 'Table 8', 4, 20, 'Completed', 'John Doe', '0123456789', '18:55', '21:05', 5, 40, false, '/images/table-sample.png'),
    ('res-4', 'seed-client', 'Quick Carcassonne Match', '05/09/2024', '16:00 - 17:00', 2, 1, 'Table 1', 2, 15, 'Cancelled', 'John Doe', '0123456789', '-', '-', 0, 0, false, '/images/table-sample.png'),
    ('res-5', 'seed-client', 'Terraforming Mars Marathon', '01/09/2024', '13:00 - 17:00', 5, 3, 'Table 3', 6, 30, 'Completed', 'John Doe', '0123456789', '13:00', '17:30', 30, 135, false, '/images/table-sample.png');
