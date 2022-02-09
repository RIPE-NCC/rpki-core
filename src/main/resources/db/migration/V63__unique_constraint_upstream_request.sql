BEGIN;

ALTER TABLE upstream_request ADD UNIQUE(requesting_ca_id);

COMMIT;
