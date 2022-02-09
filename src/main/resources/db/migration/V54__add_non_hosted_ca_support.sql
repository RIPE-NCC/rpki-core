BEGIN;

CREATE TABLE down_stream_provisioning_communicator (
  id                  BIGINT PRIMARY KEY,
  encoded_certificate BYTEA                    NOT NULL,
  encoded_crl         BYTEA                    NOT NULL,
  keystore            BYTEA,
  keystore_provider   VARCHAR(2000),
  signature_provider  VARCHAR(2000)            NOT NULL,
  keystore_type       VARCHAR(2000),
  last_issued_serial  NUMERIC,
  created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE provisioning_audit_log (
  id                      BIGINT PRIMARY KEY,
  created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
  parent_handle           TEXT                     NOT NULL,
  child_handle            TEXT                     NOT NULL,
  request_message_type    VARCHAR(100)             NOT NULL,
  request_cms_object      BYTEA                    NOT NULL,
  response_message_type   VARCHAR(100),
  response_cms_object     BYTEA,
  response_exception_type VARCHAR(100)
);

ALTER TABLE certificateauthority ADD COLUMN down_stream_provisioning_communicator_id BIGINT;

CREATE TABLE non_hosted_ca_resource_class (
  id         BIGINT DEFAULT nextval('seq_all') NOT NULL PRIMARY KEY,
  created_at TIMESTAMP WITHOUT TIME ZONE       NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE       NOT NULL,
  ca_id      BIGINT                            NOT NULL,
  name       TEXT                              NOT NULL
);

CREATE INDEX non_hosted_ca_resource_class_ca_id ON non_hosted_ca_resource_class (ca_id);

ALTER TABLE non_hosted_ca_resource_class
ADD CONSTRAINT non_hosted_ca_resource_class_ca_id_fkey FOREIGN KEY (ca_id)
REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE CASCADE;

CREATE TABLE non_hosted_ca_public_key (
  id                              BIGINT DEFAULT nextval('seq_all') PRIMARY KEY,
  created_at                      TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL,
  non_hosted_ca_resource_class_id BIGINT                   NOT NULL,
  encoded                         BYTEA                    NOT NULL,
  revoked                         BOOLEAN                  NOT NULL);

CREATE INDEX non_hosted_ca_public_key_ca_resource_class_id ON non_hosted_ca_public_key (non_hosted_ca_resource_class_id);
ALTER TABLE non_hosted_ca_public_key
ADD CONSTRAINT non_hosted_ca_public_key_ca_resource_class_fkey FOREIGN KEY (non_hosted_ca_resource_class_id)
REFERENCES non_hosted_ca_resource_class (id) ON UPDATE RESTRICT ON DELETE CASCADE;

COMMIT;
