CREATE TABLE ca_child (
  id bigint DEFAULT nextval('seq_all') not null primary key,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone NOT NULL,
  parent_ca_id bigint not null,
  child_ca_id bigint not null,
  certifiable_resources text not null);

CREATE INDEX ca_child_parent_ca_id ON ca_child (parent_ca_id);
CREATE UNIQUE INDEX ca_child_child_ca_id ON ca_child (child_ca_id);
ALTER TABLE ca_child
    ADD CONSTRAINT ca_child_parent_ca_id_fkey FOREIGN KEY (parent_ca_id) REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ca_child
    ADD CONSTRAINT ca_child_child_ca_id_fkey FOREIGN KEY (child_ca_id) REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE TABLE ca_child_public_key (
  id bigint DEFAULT nextval('seq_all') not null primary key,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone NOT NULL,
  ca_child_id bigint not null,
  encoded bytea not null,
  revoked boolean not null);

CREATE INDEX ca_child_public_key_ca_child_id ON ca_child_public_key (ca_child_id);
ALTER TABLE ca_child_public_key
    ADD CONSTRAINT ca_child_public_key_ca_child_id_fkey FOREIGN KEY (ca_child_id) REFERENCES ca_child (id) ON UPDATE RESTRICT ON DELETE CASCADE;

CREATE TABLE ca_resource_class (
  id bigint DEFAULT nextval('seq_all') not null primary key,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone NOT NULL,
  ca_id bigint not null);

CREATE INDEX ca_resource_class_ca_id ON ca_resource_class (ca_id);
ALTER TABLE ca_resource_class
    ADD CONSTRAINT ca_resource_class_ca_id_fkey FOREIGN KEY (ca_id) REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE resourcecertificate ADD COLUMN subject_public_key_id bigint;
CREATE INDEX resourcecertificate_subject_public_key_id ON resourcecertificate (subject_public_key_id);
ALTER TABLE resourcecertificate
    ADD CONSTRAINT resourcecertificate_subject_public_key_id_fkey FOREIGN KEY (subject_public_key_id) REFERENCES ca_child_public_key (id);

ALTER TABLE certificateauthority ALTER COLUMN resources DROP NOT NULL;
ALTER TABLE keypair DROP CONSTRAINT keypair_certificateauthority_id_fkey;
ALTER TABLE resourcecertificate DROP CONSTRAINT resourcecertificate_receiving_ca_id_fkey;
ALTER TABLE resourcecertificate DROP CONSTRAINT resourcecertificate_requesting_ca_id_fkey;

-- Data migration
UPDATE resourcecertificate SET requesting_ca_id = NULL WHERE issuing_ca_id = requesting_ca_id;

INSERT INTO ca_resource_class (created_at, updated_at, ca_id)
SELECT created_at, updated_at, id
  FROM certificateauthority
 WHERE type IN ('ROOT', 'HOSTED');

UPDATE keypair kp SET certificateauthority_id = (SELECT rc.id FROM ca_resource_class rc WHERE rc.ca_id = kp.certificateauthority_id);

INSERT INTO ca_child (created_at, updated_at, parent_ca_id, child_ca_id, certifiable_resources)
SELECT created_at, updated_at, parent_id, id, resources
  FROM certificateauthority
 WHERE parent_id IS NOT NULL;
UPDATE certificateauthority SET resources = NULL WHERE parent_id IS NOT NULL;
UPDATE resourcecertificate rc
   SET receiving_ca_id = (SELECT resclass.id FROM ca_resource_class resclass WHERE resclass.ca_id = rc.receiving_ca_id)
 WHERE receiving_ca_id IS NOT NULL
   AND rc.type = 'INCOMING';
UPDATE resourcecertificate rc
   SET requesting_ca_id = (SELECT child.id FROM ca_child child WHERE child.child_ca_id = rc.requesting_ca_id)
 WHERE requesting_ca_id IS NOT NULL
   AND rc.type = 'OUTGOING';

INSERT INTO ca_child_public_key (created_at, updated_at, ca_child_id, encoded, revoked)
SELECT kp.created_at, kp.updated_at, ca_child.id, cert.subject_public_key, kp.status = 'REVOKED'
  FROM keypair kp
           INNER JOIN certificateauthority ca ON kp.certificateauthority_id = ca.id
           INNER JOIN ca_child ON ca.id = ca_child.child_ca_id,
       resourcecertificate cert
 WHERE cert.id = (SELECT id FROM resourcecertificate rc WHERE rc.type = 'INCOMING' AND rc.subject_keypair_id = kp.id ORDER BY serial_number DESC LIMIT 1);
DELETE FROM resourcecertificate
 WHERE type = 'INCOMING'
   AND id IN (SELECT ca.id FROM certificateauthority ca WHERE ca.type = 'NONHOSTED');
DELETE FROM keypair WHERE type = 'REMOTE';
ALTER TABLE keypair DROP COLUMN type;

ALTER TABLE certificateauthority RENAME COLUMN resources TO certifiable_resources;
ALTER TABLE certificateauthority ADD CONSTRAINT root_ca_has_certifiable_resources CHECK (
  CASE
    WHEN type = 'ROOT' THEN certifiable_resources IS NOT NULL
    ELSE certifiable_resources IS NULL
  END);
ALTER TABLE keypair RENAME COLUMN certificateauthority_id TO ca_resource_class_id;
ALTER TABLE keypair ADD CONSTRAINT keypair_ca_resource_class_id_fkey FOREIGN KEY (ca_resource_class_id) REFERENCES ca_resource_class (id) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE resourcecertificate RENAME COLUMN receiving_ca_id TO receiving_ca_resource_class_id;
ALTER TABLE resourcecertificate ADD CONSTRAINT resourcecertificate_ca_resource_class_id_fkey FOREIGN KEY (receiving_ca_resource_class_id) REFERENCES ca_resource_class (id) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE resourcecertificate RENAME COLUMN requesting_ca_id TO requesting_ca_child_id;
ALTER TABLE resourcecertificate ADD CONSTRAINT resourcecertificate_ca_child_id_fkey FOREIGN KEY (requesting_ca_child_id) REFERENCES ca_child (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
