ALTER TABLE aspaentity ADD COLUMN profile_version bigint;
/* When this SQL is executed, no objects have transitioned to the new profile */
UPDATE aspaentity SET profile_version = 14;

ALTER TABLE aspaentity ALTER COLUMN profile_version SET NOT NULL;
/*
 * All CAs need to be revisisted for a new ASPA profile
 *
 * This will cause the Public Repository Publication Service to visit all CAs
 */
UPDATE certificateauthority SET configuration_updated_at = NOW() WHERE type != 'NONHOSTED';