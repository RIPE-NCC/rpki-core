ALTER TABLE crlentity ADD COLUMN version BIGINT;
UPDATE crlentity SET VERSION = 1;
ALTER TABLE crlentity ALTER COLUMN version SET NOT NULL;

ALTER TABLE roaconfiguration ADD COLUMN version BIGINT;
UPDATE roaconfiguration SET VERSION = 1;
ALTER TABLE roaconfiguration ALTER COLUMN version SET NOT NULL;

ALTER TABLE down_stream_provisioning_communicator ADD COLUMN version BIGINT;
UPDATE down_stream_provisioning_communicator SET VERSION = 1;
ALTER TABLE down_stream_provisioning_communicator ALTER COLUMN version SET NOT NULL;

ALTER TABLE hsm_key ADD COLUMN version BIGINT;
UPDATE hsm_key SET VERSION = 1;
ALTER TABLE hsm_key ALTER COLUMN version SET NOT NULL;

ALTER TABLE hsm_key_store ADD COLUMN version BIGINT;
UPDATE hsm_key_store SET VERSION = 1;
ALTER TABLE hsm_key_store ALTER COLUMN version SET NOT NULL;

ALTER TABLE resourcecertificate ADD COLUMN version BIGINT;
UPDATE resourcecertificate SET VERSION = 1;
ALTER TABLE resourcecertificate ALTER COLUMN version SET NOT NULL;

ALTER TABLE keypair ADD COLUMN version BIGINT;
UPDATE keypair SET VERSION = 1;
ALTER TABLE keypair ALTER COLUMN version SET NOT NULL;

ALTER TABLE manifestentity ADD COLUMN version BIGINT;
UPDATE manifestentity SET VERSION = 1;
ALTER TABLE manifestentity ALTER COLUMN version SET NOT NULL;

ALTER TABLE property ADD COLUMN version BIGINT;
UPDATE property SET VERSION = 1;
ALTER TABLE property ALTER COLUMN version SET NOT NULL;

ALTER TABLE provisioning_audit_log ADD COLUMN version BIGINT;
UPDATE provisioning_audit_log SET VERSION = 1;
ALTER TABLE provisioning_audit_log ALTER COLUMN version SET NOT NULL;

ALTER TABLE non_hosted_ca_public_key ADD COLUMN version BIGINT;
UPDATE non_hosted_ca_public_key SET VERSION = 1;
ALTER TABLE non_hosted_ca_public_key ALTER COLUMN version SET NOT NULL;

ALTER TABLE roa_alert_configuration ADD COLUMN version BIGINT;
UPDATE roa_alert_configuration SET VERSION = 1;
ALTER TABLE roa_alert_configuration ALTER COLUMN version SET NOT NULL;

ALTER TABLE roaentity ADD COLUMN version BIGINT;
UPDATE roaentity SET VERSION = 1;
ALTER TABLE roaentity ALTER COLUMN version SET NOT NULL;

ALTER TABLE published_object ALTER COLUMN version TYPE BIGINT;
ALTER TABLE ta_published_object ALTER COLUMN version TYPE BIGINT;
