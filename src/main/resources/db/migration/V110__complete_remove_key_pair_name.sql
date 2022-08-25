DROP TRIGGER key_pair_row_insert_trigger ON keypair;
DROP FUNCTION key_pair_insert_trigger();
ALTER TABLE keypair DROP COLUMN name;
