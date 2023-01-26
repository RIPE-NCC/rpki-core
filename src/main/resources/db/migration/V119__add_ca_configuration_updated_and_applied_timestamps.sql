ALTER TABLE certificateauthority
  ADD COLUMN configuration_updated_at TIMESTAMP(3) WITH TIME ZONE,
  ADD COLUMN configuration_applied_at TIMESTAMP(3) WITH TIME ZONE,
  ADD CONSTRAINT configuration_checked_applied CHECK (configuration_updated_at <> configuration_applied_at);
COMMENT ON COLUMN certificateauthority.configuration_updated_at
     IS 'Timestamp of the last update to the CA configuration (ASPA, ROA)';
COMMENT ON COLUMN certificateauthority.configuration_applied_at
     IS 'Timestamp of the last time the CA configuration (ASPA, ROA) was applied against the issued CMS objects';

CREATE INDEX certificateauthority_configuration_updated_idx
    ON certificateauthority (configuration_updated_at)
 WHERE configuration_updated_at > configuration_applied_at;
COMMENT ON INDEX certificateauthority_configuration_updated_idx
     IS 'Index to find CAs where configuration needs to be applied to update issued CMS objects';
