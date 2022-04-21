UPDATE resourcecertificate
   SET requesting_ca_id = NULL
 WHERE requesting_ca_id IS NOT NULL
   AND status <> 'CURRENT';

ALTER TABLE resourcecertificate
  DROP CONSTRAINT resourcecertificate_requesting_ca_id,
  ADD CONSTRAINT resourcecertificate_requesting_ca_id
           CHECK (requesting_ca_id IS NULL OR (type = 'OUTGOING' AND status = 'CURRENT'));
