ALTER TABLE aspaconfiguration_providers RENAME TO aspaconfiguration_providers_v14;

CREATE TABLE aspaconfiguration_providers (
    aspa_configuration_id BIGINT NOT NULL,
    providers numeric NOT NULL
    );
ALTER TABLE aspaconfiguration_providers
    ADD CONSTRAINT aspaconfiguration_providers_pkey PRIMARY KEY (aspa_configuration_id, providers);
ALTER TABLE aspaconfiguration_providers
    ADD CONSTRAINT aspaconfiguration_providers_id_fkey FOREIGN KEY (aspa_configuration_id) REFERENCES aspaconfiguration(id) ON UPDATE RESTRICT ON DELETE CASCADE;

INSERT INTO aspaconfiguration_providers(aspa_configuration_id, providers) SELECT aspaconfiguration_id, provider_asn FROM aspaconfiguration_providers_v14;
DROP TABLE aspaconfiguration_providers_v14;