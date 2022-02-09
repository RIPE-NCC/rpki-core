BEGIN;

-- ALTER TABLE published_object ADD COLUMN directory text;
--
-- UPDATE published_object
--    SET directory = certificate_repository_location
--   FROM keypair
--  WHERE keypair.id = published_object.issuing_key_pair_id;
--
-- ALTER TABLE published_object ALTER COLUMN directory SET NOT NULL;
--
-- ALTER TABLE keypair DROP COLUMN certificate_repository_location;

---

ALTER TABLE keypair ADD COLUMN certificate_repository_location TEXT;

UPDATE keypair k
   SET certificate_repository_location = (
     SELECT directory
     FROM published_object po
     WHERE k.id = po.issuing_key_pair_id
     ORDER BY CASE
       WHEN po.status = 'PUBLISHED' THEN 0
       ELSE 1
     END
     LIMIT 1
   );

ALTER TABLE published_object DROP COLUMN directory;

COMMIT;
