CREATE TABLE aspaentity
(
    id                  bigint                   PRIMARY KEY,
    certificate_id      bigint                   NOT NULL,
    created_at          timestamp with time zone NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    published_object_id bigint                   NOT NULL,
    version             bigint                   NOT NULL
);

CREATE INDEX aspaentity_cert_id ON aspaentity USING btree (certificate_id);
CREATE INDEX aspaentity_published_object_id ON aspaentity USING btree (published_object_id);

ALTER TABLE ONLY aspaentity
    ADD CONSTRAINT aspaentity_certificate_id_fkey
        FOREIGN KEY (certificate_id) REFERENCES resourcecertificate (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY aspaentity
    ADD CONSTRAINT aspaentity_published_object_fk FOREIGN KEY (published_object_id) REFERENCES published_object (id);
