BEGIN;

-- Replace ca_resource_class_id in keypair table with ca_id
-- and drop intermediate table
--
ALTER TABLE keypair
    DROP CONSTRAINT keypair_ca_resource_class_id_fkey;

ALTER TABLE keypair
    ADD COLUMN ca_id BIGINT;

UPDATE keypair kp
SET ca_id = (SELECT ca_id
             FROM ca_resource_class rc
             WHERE rc.id = kp.ca_resource_class_id
               AND rc.name = 'DEFAULT'
             LIMIT 1);

ALTER TABLE keypair
    ALTER COLUMN ca_id SET NOT NULL;

ALTER TABLE keypair
    ADD CONSTRAINT keypair_ca_id_fk
        FOREIGN KEY (ca_id)
            REFERENCES certificateauthority (id)
            ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE keypair
    DROP COLUMN ca_resource_class_id;

DROP TABLE ca_resource_class;


-- Do the same for delegated CAs
-- Replace non_hosted_ca_resource_class_id in non_hosted_ca_public_key table with ca_id
--
ALTER TABLE non_hosted_ca_public_key
    DROP CONSTRAINT non_hosted_ca_public_key_ca_resource_class_fkey;

ALTER TABLE non_hosted_ca_public_key
    ADD COLUMN ca_id BIGINT;

UPDATE non_hosted_ca_public_key pk
SET ca_id = (SELECT ca_id
             FROM non_hosted_ca_resource_class rc
             WHERE rc.id = pk.non_hosted_ca_resource_class_id
               AND rc.name = 'DEFAULT'
             LIMIT 1);

ALTER TABLE non_hosted_ca_public_key
    ALTER COLUMN ca_id SET NOT NULL;

ALTER TABLE non_hosted_ca_public_key
    ADD CONSTRAINT non_hosted_ca_public_key_ca_id_fk
        FOREIGN KEY (ca_id)
            REFERENCES certificateauthority (id)
            ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE non_hosted_ca_public_key
    DROP COLUMN non_hosted_ca_resource_class_id;

DROP TABLE non_hosted_ca_resource_class;

COMMIT;
