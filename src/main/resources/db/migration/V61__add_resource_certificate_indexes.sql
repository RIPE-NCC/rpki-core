BEGIN;

DROP INDEX IF EXISTS idx_rc_pair_id_validity_not_after_status_type;
CREATE INDEX idx_rc_pair_id_validity_not_after_status_type ON resourcecertificate(signing_keypair_id, validity_not_after, status, type);

COMMIT;
