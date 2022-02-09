BEGIN;

CREATE TABLE deleted_roaconfiguration_prefixes
(
    roaconfiguration_id BIGINT  NOT NULL,
    asn                 NUMERIC NOT NULL,
    prefix_type_id      INTEGER NOT NULL,
    prefix_start        NUMERIC NOT NULL,
    prefix_end          NUMERIC NOT NULL,
    maximum_length      INTEGER,
    deleted_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE deleted_roaconfiguration_prefixes
    ADD CONSTRAINT deleted_roaconfiguration_ca_id_fkey FOREIGN KEY (roaconfiguration_id)
        REFERENCES roaconfiguration (id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE deleted_roaconfiguration_prefixes
    ADD CONSTRAINT deleted_roaconfiguration_prefix_type_fkey FOREIGN KEY (prefix_type_id)
        REFERENCES ip_resource_type (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

COMMIT;

