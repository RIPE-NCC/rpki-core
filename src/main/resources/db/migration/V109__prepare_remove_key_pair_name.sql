ALTER TABLE keypair
  ALTER COLUMN name DROP NOT NULL,
  DROP COLUMN remote_public_key;

CREATE FUNCTION key_pair_insert_trigger()
RETURNS trigger AS $$
BEGIN
  IF NEW.name IS NULL THEN
    NEW.name := 'KEY-' || NEW.id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER key_pair_row_insert_trigger
        BEFORE INSERT ON keypair
           FOR EACH ROW
       EXECUTE PROCEDURE key_pair_insert_trigger();
