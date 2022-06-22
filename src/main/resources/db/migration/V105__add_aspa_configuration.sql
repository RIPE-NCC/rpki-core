CREATE TABLE aspaconfiguration
(
    id                      bigint                   NOT NULL PRIMARY KEY,
    customer_asn            numeric                  NOT NULL,
    created_at              timestamp with time zone NOT NULL,
    updated_at              timestamp with time zone NOT NULL,
    certificateauthority_id bigint                   NOT NULL,
    version                 bigint                   NOT NULL
);

ALTER TABLE ONLY aspaconfiguration
    ADD CONSTRAINT aspaconfiguration_unique_per_ca UNIQUE (certificateauthority_id);

ALTER TABLE ONLY aspaconfiguration
    ADD CONSTRAINT aspaconfiguration_ca_id_fkey FOREIGN KEY (certificateauthority_id)
        REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE CASCADE;


CREATE TABLE aspaconfiguration_provider_asns
(
    aspaconfiguration_id bigint  NOT NULL,
    asn                  numeric NOT NULL,
    prefix_type_id       integer NOT NULL
);

ALTER TABLE ONLY aspaconfiguration_provider_asns
    ADD CONSTRAINT aspaconfiguration_ignored_pkey PRIMARY KEY (aspaconfiguration_id, asn, prefix_type_id);

ALTER TABLE ONLY aspaconfiguration_provider_asns
    ADD CONSTRAINT aspaconfiguration_ca_id_fkey FOREIGN KEY (aspaconfiguration_id)
        REFERENCES aspaconfiguration (id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE ONLY aspaconfiguration_provider_asns
    ADD CONSTRAINT aspaconfiguration_prefix_type_fkey FOREIGN KEY (prefix_type_id)
        REFERENCES ip_resource_type (id) ON UPDATE RESTRICT ON DELETE RESTRICT;


CREATE INDEX aspaconfiguration_provider_asns_prefix_type_id_idx ON aspaconfiguration_provider_asns(prefix_type_id);
