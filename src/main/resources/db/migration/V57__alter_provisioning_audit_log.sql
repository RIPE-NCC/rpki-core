BEGIN;

ALTER TABLE provisioning_audit_log ADD COLUMN summary text NOT NULL;
ALTER TABLE provisioning_audit_log ADD COLUMN executiontime TIMESTAMP WITH TIME ZONE NOT NULL;
ALTER TABLE provisioning_audit_log ADD COLUMN provisioning_cms_object bytea NOT NULL;
ALTER TABLE provisioning_audit_log ADD COLUMN non_hosted_ca_uuid text NOT NULL;

ALTER TABLE provisioning_audit_log DROP COLUMN request_cms_object;
ALTER TABLE provisioning_audit_log DROP COLUMN response_cms_object;
ALTER TABLE provisioning_audit_log DROP COLUMN response_message_type;

ALTER TABLE provisioning_audit_log DROP COLUMN response_exception_type;
ALTER TABLE provisioning_audit_log DROP COLUMN parent_handle;
ALTER TABLE provisioning_audit_log DROP COLUMN child_handle;

COMMIT;
