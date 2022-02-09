BEGIN;

ALTER TABLE roa_alert_configuration ADD COLUMN frequency varchar(10) DEFAULT 'DAILY';

COMMIT;
