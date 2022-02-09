DROP INDEX idx_rc_pair_id_validity_not_after_status_type;

CREATE INDEX resourcecertificate_validity_not_after_status_idx ON resourcecertificate (validity_not_after, status)
 WHERE type = 'OUTGOING';
COMMENT ON INDEX resourcecertificate_validity_not_after_status_idx
 IS 'find OUTGOING certificates that can be set to EXPIRED and EXPIRED certificates that can be deleted';

CREATE INDEX published_object_withdrawn_validity_not_after_idx ON published_object (validity_not_after)
 WHERE status = 'WITHDRAWN';
COMMENT ON INDEX published_object_withdrawn_validity_not_after_idx
 IS 'find expired, WITHDRAWN objects that can be deleted';
