-- Development-only schema cutover. Pre-V007 reservations do not contain the
-- lifecycle audit required below, and overlapping confirmed bookings cannot be
-- resolved without a business decision. Reset the development database rather
-- than weakening constraints or inventing audit history.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM reservations) THEN
    RAISE EXCEPTION
      'V007 requires an empty reservations table; reset the development database instead of upgrading legacy reservations';
  END IF;
END
$$;

-- The registered customer's identity subject at booking; NULL for guests.
ALTER TABLE reservations ADD COLUMN customer_subject TEXT;
ALTER TABLE reservations ADD CONSTRAINT chk_reservations_customer_subject
  CHECK (customer_subject IS NULL OR customer_subject <> '');
CREATE INDEX idx_reservations_customer_subject ON reservations(customer_subject, id)
  WHERE customer_subject IS NOT NULL;

-- A completed visit keeps its check-in audit fields and gains a checkout time.
ALTER TABLE reservations ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE reservations DROP CONSTRAINT chk_reservations_checked_in;
ALTER TABLE reservations ADD CONSTRAINT chk_reservations_checked_in
  CHECK ((status IN ('CHECKED_IN', 'COMPLETED')) = (checked_in_at IS NOT NULL));
ALTER TABLE reservations ADD CONSTRAINT chk_reservations_completed
  CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL));

-- Staff state why a booking was released as a no-show.
ALTER TABLE reservations ADD COLUMN no_show_reason VARCHAR(500);
ALTER TABLE reservations ADD CONSTRAINT chk_reservations_no_show_reason
  CHECK (status <> 'NO_SHOW' OR (no_show_reason IS NOT NULL AND no_show_reason <> ''));

-- Last line of defence against double booking: two confirmed reservations of
-- one table can never overlap, whatever code path wrote them. Windows are
-- half-open, so back-to-back bookings do not conflict. The application also
-- locks the table row and checks checked-in visits, which have no fixed end.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE reservations ADD CONSTRAINT excl_reservations_confirmed_table
  EXCLUDE USING gist (table_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
  WHERE (status = 'CONFIRMED');
