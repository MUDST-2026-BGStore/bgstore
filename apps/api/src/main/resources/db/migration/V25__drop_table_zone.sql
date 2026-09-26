-- The zone concept was removed from table management in the branch redesign:
-- the contract, API, and UI no longer carry a zone for store tables. Drop the
-- column so inserts performed through the new contract no longer violate its
-- NOT NULL constraint.
ALTER TABLE store_table DROP COLUMN zone;
