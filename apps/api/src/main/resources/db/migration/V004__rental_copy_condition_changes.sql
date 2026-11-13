-- Append-only ledger of rental copy condition changes. rental_copies keeps the
-- current operational status; this table records who changed it, when and why.
-- A reason is mandatory when a copy leaves the rentable pool.
CREATE TABLE rental_copy_condition_changes
(
  id               UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id        UUID NOT NULL,
  copy_id          UUID NOT NULL,
  from_status      VARCHAR(20) NOT NULL,
  to_status        VARCHAR(20) NOT NULL,
  reason           VARCHAR(500),
  operator_subject TEXT NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (branch_id, copy_id) REFERENCES rental_copies(branch_id, id) ON DELETE CASCADE,
  CONSTRAINT chk_rental_copy_condition_changes_from
    CHECK (from_status IN ('USABLE', 'OUT_OF_SERVICE')),
  CONSTRAINT chk_rental_copy_condition_changes_to
    CHECK (to_status IN ('USABLE', 'OUT_OF_SERVICE', 'RETIRED')),
  CONSTRAINT chk_rental_copy_condition_changes_transition CHECK (from_status <> to_status),
  CONSTRAINT chk_rental_copy_condition_changes_reason
    CHECK (reason IS NULL OR reason <> ''),
  CONSTRAINT chk_rental_copy_condition_changes_reason_required
    CHECK (to_status = 'USABLE' OR reason IS NOT NULL),
  CONSTRAINT chk_rental_copy_condition_changes_operator CHECK (operator_subject <> '')
);

-- Serves "latest change per copy" for a page of copies.
CREATE INDEX idx_rental_copy_condition_changes_latest
  ON rental_copy_condition_changes(copy_id, created_at DESC, id DESC);
