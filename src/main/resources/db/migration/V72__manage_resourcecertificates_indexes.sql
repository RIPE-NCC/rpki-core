BEGIN;

DROP INDEX IF EXISTS idx_resource_certificate_serial_number;
DROP INDEX IF EXISTS upstream_request_ca_id;
DROP INDEX IF EXISTS ixd_ta_published_object_status;
DROP INDEX IF EXISTS idx_part2_published_object;
DROP INDEX IF EXISTS idx_resourcecertificate_subject_public_key;
CREATE INDEX idx_resourcecertificate_subject_public_key ON resourcecertificate(subject_public_key);

COMMIT;
