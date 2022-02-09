-- Adds missing indexes on foreign key source columns. In some cases these indexes are not necessary
-- (never join, do not update the target table's referenced columns), but for simplicity it is easier
-- to always have these.
--
-- See https://www.cybertec-postgresql.com/en/index-your-foreign-key/ for the query used to find the
-- missing indexes.

CREATE INDEX certificateauthority_parent_id_idx ON certificateauthority (parent_id);

CREATE INDEX keypair_ca_id_idx ON keypair (ca_id);

CREATE INDEX resourcecertificate_subject_public_key_id_idx ON resourcecertificate (subject_public_key_id);

CREATE INDEX non_hosted_ca_public_key_ca_id_idx ON non_hosted_ca_public_key (ca_id);

-- These two indexes we don't really need since the target table (`ip_resource_type`) is never updated.
CREATE INDEX roaconfiguration_prefixes_prefix_type_idx ON roaconfiguration_prefixes (prefix_type_id);
CREATE INDEX deleted_roaconfiguration_prefixes_prefix_type_idx ON deleted_roaconfiguration_prefixes (prefix_type_id);

CREATE INDEX deleted_roaconfiguration_prefixes_roaconfiguration_id_idx ON deleted_roaconfiguration_prefixes (roaconfiguration_id);

-- `hsm_key_alias_store_id` is a unique constraints with incorrect column ordering to serve as an index
-- on the `store_id` foreign key constraint. So add a new index on just `store_id`. Or find out if the
-- table is every queried by `alias`. If not, we could just inverse the ordering of the existing indexes.
CREATE INDEX hsm_key_store_id_idx ON hsm_key (store_id);

CREATE INDEX hsm_certificate_chain_key_id_idx ON hsm_certificate_chain (key_id);
