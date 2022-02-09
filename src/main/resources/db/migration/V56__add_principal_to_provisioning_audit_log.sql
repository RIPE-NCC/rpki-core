BEGIN;

ALTER TABLE provisioning_audit_log ADD COLUMN principal text NOT NULL;

COMMIT;
