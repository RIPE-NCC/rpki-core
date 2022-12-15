-- Delete ROA alert configuration when CA is deleted, making this foreign key consistent
-- with how the ASPA and ROA configurations work.
ALTER TABLE roa_alert_configuration
       DROP CONSTRAINT alert_subscription_certificateauthority_id_fkey,
        ADD CONSTRAINT alert_subscription_certificateauthority_id_fkey
                FOREIGN KEY (certificateauthority_id) REFERENCES certificateauthority (id)
                ON UPDATE RESTRICT ON DELETE CASCADE;
