BEGIN;

ALTER TABLE published_object ADD COLUMN directory text;

UPDATE published_object
   SET directory = certificate_repository_location
  FROM keypair
 WHERE keypair.id = published_object.issuing_key_pair_id;

ALTER TABLE published_object ALTER COLUMN directory SET NOT NULL;

ALTER TABLE keypair DROP COLUMN certificate_repository_location;

COMMIT;
