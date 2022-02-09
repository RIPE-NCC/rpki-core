BEGIN;

ALTER TABLE roaconfiguration DROP CONSTRAINT roaconfiguration_unique_per_ca;
ALTER TABLE roaconfiguration ADD CONSTRAINT roaconfiguration_unique_per_ca UNIQUE(certificateauthority_id);

COMMIT;
