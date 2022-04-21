ALTER TABLE resourcecertificate
  ADD COLUMN requesting_ca_id BIGINT,
  ADD CONSTRAINT requesting_ca_id_fk FOREIGN KEY (requesting_ca_id) REFERENCES certificateauthority (id)
    ON UPDATE RESTRICT ON DELETE SET NULL,
  ADD CONSTRAINT resourcecertificate_requesting_ca_id CHECK (type = 'OUTGOING' OR requesting_ca_id IS NULL);
CREATE INDEX resourcecertificate_request_ca_id_idx ON resourcecertificate (requesting_ca_id);

COMMENT ON COLUMN resourcecertificate.requesting_ca_id IS 'Child CA that requested this certificate';

-- Link hosted CAs
UPDATE resourcecertificate outgoing
   SET requesting_ca_id = ca.id
  FROM certificateauthority ca
 WHERE outgoing.type = 'OUTGOING'
   AND outgoing.embedded = FALSE
   AND EXISTS (SELECT 1
                 FROM resourcecertificate incoming INNER JOIN keypair kp ON incoming.subject_keypair_id = kp.id
                WHERE incoming.type = 'INCOMING'
                  AND incoming.subject_public_key = outgoing.subject_public_key
                  AND kp.ca_id = ca.id);

-- Link delegated CAs
UPDATE resourcecertificate outgoing
   SET requesting_ca_id = pk.ca_id
  FROM non_hosted_ca_public_key pk
 WHERE outgoing.type = 'OUTGOING'
   AND outgoing.embedded = FALSE
   AND outgoing.subject_public_key_id = pk.id;
