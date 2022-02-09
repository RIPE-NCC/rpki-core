-- Change all 'timestamp without time zone' columns to have a time zone.
-- Otherwise Hibernate stores times in local time, instead of using UTC.

alter table certificateauthority alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone, alter column last_message_seen_at type timestamp with time zone;

alter table commandaudit alter column executiontime type timestamp with time zone;

alter table crlentity alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table down_stream_provisioning_communicator alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table downloadable_item alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table eventaudit alter column executiontime type timestamp with time zone;

alter table keypair alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table keypair_statushistory alter column changed_at type timestamp with time zone;

alter table manifestentity alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table property alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table provisioning_audit_log alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table publicationobjectentity alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table published_object alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table resourcecertificate alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone, alter column validity_not_before type timestamp with time zone, alter column validity_not_after type timestamp with time zone, alter column revocationtime type timestamp with time zone;

alter table roa_alert_configuration alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table roaconfiguration alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;

alter table roaentity alter column created_at type timestamp with time zone, alter column updated_at type timestamp with time zone;
