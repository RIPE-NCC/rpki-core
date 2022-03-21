ALTER TABLE certificateauthority
    ALTER COLUMN uuid SET NOT NULL,
    ALTER COLUMN uuid TYPE uuid USING uuid::uuid,
    ADD CONSTRAINT certificateauthority_uuid_unique UNIQUE (uuid) ;

ALTER TABLE provisioning_audit_log
    ALTER COLUMN non_hosted_ca_uuid TYPE uuid USING non_hosted_ca_uuid::uuid,
    ALTER COLUMN entry_uuid TYPE uuid USING entry_uuid::uuid,
    ADD CONSTRAINT provisioning_audit_log_unique_entry_uuid UNIQUE (entry_uuid);

CREATE INDEX provisioning_audit_log_non_hosted_ca_uuid_idx ON provisioning_audit_log (non_hosted_ca_uuid);
