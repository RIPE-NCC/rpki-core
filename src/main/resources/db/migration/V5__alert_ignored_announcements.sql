ALTER TABLE alert_subscription RENAME TO roa_alert_configuration;

ALTER TABLE roa_alert_configuration ALTER COLUMN email TYPE text;

CREATE TABLE roa_alert_configuration_ignored (
    roa_alert_configuration_id bigint NOT NULL,
    asn varchar(50) NOT NULL,
    prefix varchar(50) NOT NULL);

ALTER TABLE roa_alert_configuration_ignored
    ADD CONSTRAINT roa_alert_configuration_ignored_pkey PRIMARY KEY (roa_alert_configuration_id, asn, prefix);
ALTER TABLE roa_alert_configuration_ignored
    ADD CONSTRAINT roa_alert_configuration_ignored_ca_id_fkey FOREIGN KEY (roa_alert_configuration_id) REFERENCES roa_alert_configuration(id) ON UPDATE RESTRICT ON DELETE CASCADE;
