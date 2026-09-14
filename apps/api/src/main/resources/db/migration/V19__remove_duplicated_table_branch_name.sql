-- The immutable foreign key is now the only persisted table ownership value.
-- API callers still send/read a branch name, resolved through the branch module.
DROP INDEX IF EXISTS ix_store_table_branch;
ALTER TABLE store_table DROP COLUMN branch;
