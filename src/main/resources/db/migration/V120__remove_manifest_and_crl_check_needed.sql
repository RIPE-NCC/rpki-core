UPDATE certificateauthority
   SET configuration_updated_at = NOW(),
       configuration_applied_at = NOW() + '1 millisecond'
 WHERE type <> 'NONHOSTED'
   AND (configuration_updated_at IS NULL OR configuration_applied_at IS NULL);

ALTER TABLE certificateauthority
  DROP COLUMN manifest_and_crl_check_needed,
  ADD CONSTRAINT configuration_timestamps_required_check CHECK (
      CASE type
      WHEN 'NONHOSTED' THEN configuration_updated_at IS NULL AND configuration_applied_at IS NULL
      ELSE configuration_updated_at IS NOT NULL AND configuration_applied_at IS NOT NULL
      END
  );
