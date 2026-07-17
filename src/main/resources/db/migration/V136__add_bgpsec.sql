CREATE TABLE bgpsec_configuration
(
    id                      bigint                   NOT NULL PRIMARY KEY,
    asn                     numeric                  NOT NULL,
    key_identifier          text                     NOT NULL,
    certificate_request     text                     NOT NULL,
    router_id               bigint,
    created_at              timestamp with time zone NOT NULL,
    updated_at              timestamp with time zone NOT NULL,
    certificateauthority_id bigint                   NOT NULL,
    version                 bigint                   NOT NULL
);

ALTER TABLE ONLY bgpsec_configuration
    ADD CONSTRAINT bgpsec_configuration_unique_per_ca
        UNIQUE NULLS NOT DISTINCT (certificateauthority_id, asn, key_identifier, router_id);

ALTER TABLE ONLY bgpsec_configuration
    ADD CONSTRAINT bgpsec_configuration_ca_id_fkey FOREIGN KEY (certificateauthority_id)
        REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE CASCADE;

CREATE INDEX bgpsec_configuration_ca_id ON bgpsec_configuration(certificateauthority_id);
CREATE INDEX bgpsec_configuration_asn ON bgpsec_configuration(asn);


CREATE TABLE bgpsec_entity
(
    id                  bigint                   NOT NULL PRIMARY KEY,
    asn                 numeric                  NOT NULL,
    key_identifier      text                     NOT NULL,
    router_id           bigint,
    certificate_id      bigint                   NOT NULL,
    published_object_id bigint                   NOT NULL,
    created_at          timestamp with time zone NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    version             bigint                   NOT NULL
);

ALTER TABLE ONLY bgpsec_entity
    ADD CONSTRAINT bgpsec_entity_po_id_fkey FOREIGN KEY (published_object_id)
        REFERENCES published_object (id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE ONLY bgpsec_entity
    ADD CONSTRAINT bgpsec_entity_unique_per_ca
        UNIQUE NULLS NOT DISTINCT (asn, key_identifier, router_id);

CREATE INDEX bgpsec_entity_po_id ON bgpsec_entity (published_object_id);
CREATE INDEX bgpsec_entity_cert_id ON bgpsec_entity (certificate_id);

CREATE TABLE bgpsec_certificate
(
    id                      bigint                   NOT NULL PRIMARY KEY,
    serial_number           numeric                  NOT NULL,
    subject                 character varying(2000)  NOT NULL,
    subject_public_key      bytea                    NOT NULL,
    issuer                  character varying(2000)  NOT NULL,
    validity_not_before     timestamp                NOT NULL,
    validity_not_after      timestamp                NOT NULL,
    encoded                 bytea                    NOT NULL,
    signing_keypair_id      bigint                   NOT NULL,
    asns                    text                     NOT NULL,
    status                  CHARACTER VARYING(10)    NOT NULL DEFAULT 'PENDING',
    revocationtime          TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    version                 bigint                   NOT NULL
);

ALTER TABLE bgpsec_certificate
    ADD CONSTRAINT bgpsec_certificate_signing_keypair_id_fkey FOREIGN KEY (signing_keypair_id)
        REFERENCES keypair (id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE bgpsec_entity
    ADD CONSTRAINT bgpsec_entity_cert_id_fkey FOREIGN KEY (certificate_id)
        REFERENCES bgpsec_certificate (id) ON UPDATE RESTRICT ON DELETE CASCADE;

CREATE INDEX bgpsec_certificate_signing_keypair_id ON bgpsec_certificate(signing_keypair_id);
CREATE INDEX bgpsec_certificate_serial ON bgpsec_certificate(serial_number);
