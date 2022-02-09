BEGIN;

DROP INDEX IF EXISTS resourcecertificate_publicationuri_idx;
DROP INDEX IF EXISTS resourcecertificate_subject_public_key_id;
DROP INDEX IF EXISTS commandaudit_ca_id_and_version_and_commandgroup_is_user_idx;
DROP INDEX IF EXISTS commandaudit_ca_id_and_commandtype_is_update_roa;
DROP INDEX IF EXISTS certificateauthority_parent_id_fk;
DROP INDEX IF EXISTS certificateauthority_name;
DROP INDEX IF EXISTS non_hosted_ca_resource_class_ca_id;
DROP INDEX IF EXISTS non_hosted_ca_public_key_ca_resource_class_id;

COMMIT;
