ALTER TABLE resourcecertificate ADD COLUMN asn_inherited BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE resourcecertificate ADD COLUMN ipv4_inherited BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE resourcecertificate ADD COLUMN ipv6_inherited BOOLEAN NOT NULL DEFAULT false;

UPDATE resourcecertificate
   SET asn_inherited = true,
       ipv4_inherited = true,
       ipv6_inherited = true
 WHERE resources = '';

CREATE FUNCTION update_inherited_resources() RETURNS trigger AS
$$
DECLARE
  inherited BOOLEAN := NEW.resources = '';
BEGIN
  IF NEW.asn_inherited IS NULL THEN
    NEW.asn_inherited = inherited;
    NEW.ipv4_inherited = inherited;
    NEW.ipv6_inherited = inherited;
  END IF;
  RETURN NEW;
END;
$$
LANGUAGE 'plpgsql';

CREATE TRIGGER set_inherited_resources BEFORE INSERT ON resourcecertificate
  FOR EACH ROW
  WHEN (NEW.asn_inherited IS NULL OR NEW.ipv4_inherited IS NULL OR NEW.ipv6_inherited IS NULL)
EXECUTE PROCEDURE update_inherited_resources();
