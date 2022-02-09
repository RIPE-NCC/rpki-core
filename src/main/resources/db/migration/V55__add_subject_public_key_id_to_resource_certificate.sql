BEGIN;

ALTER TABLE resourcecertificate ADD COLUMN subject_public_key_id bigint;
CREATE INDEX resourcecertificate_subject_public_key_id ON resourcecertificate (subject_public_key_id);
ALTER TABLE resourcecertificate
    ADD CONSTRAINT resourcecertificate_subject_public_key_id_fkey FOREIGN KEY (subject_public_key_id) REFERENCES non_hosted_ca_public_key (id);

COMMIT;
