-- Branch ownership is keyed by the immutable branch id.  The existing branch
-- text is retained temporarily as display data for the public table contract,
-- but all new writes and authorization checks use these foreign keys.
ALTER TABLE store_table ADD COLUMN branch_id UUID;

UPDATE store_table t
SET branch_id = b.id
FROM branch b
WHERE lower(b.name) = lower(t.branch);

-- Seed data is disposable in this pre-production application. Do not leave an
-- unowned table that could bypass branch policy.
DELETE FROM store_table WHERE branch_id IS NULL;

ALTER TABLE store_table
    ALTER COLUMN branch_id SET NOT NULL,
    ADD CONSTRAINT fk_store_table_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE RESTRICT;

CREATE INDEX ix_store_table_branch_id ON store_table (branch_id);
ALTER TABLE store_table DROP CONSTRAINT uq_store_table_name_branch;
ALTER TABLE store_table ADD CONSTRAINT uq_store_table_name_branch_id UNIQUE (name, branch_id);

ALTER TABLE reservation ADD COLUMN branch_id UUID;

UPDATE reservation r
SET branch_id = t.branch_id
FROM store_table t
WHERE t.id = r.table_id;

DELETE FROM reservation WHERE branch_id IS NULL;

ALTER TABLE reservation
    ALTER COLUMN branch_id SET NOT NULL,
    ADD CONSTRAINT fk_reservation_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE RESTRICT;

CREATE INDEX ix_reservation_branch_created ON reservation (branch_id, created_at DESC);
