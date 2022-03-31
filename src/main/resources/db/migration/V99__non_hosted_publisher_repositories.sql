CREATE TABLE non_hosted_publisher_repository (
  id                              BIGINT DEFAULT nextval('seq_all') PRIMARY KEY,
  version                         BIGINT NOT NULL,
  created_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  ca_id                           BIGINT                   NOT NULL,
  publisher_handle                UUID                     NOT NULL,
  publisher_request               TEXT                     NOT NULL,
  repository_response             TEXT                     NOT NULL,
  CONSTRAINT non_hosted_publisher_ca_id_fk FOREIGN KEY (ca_id) REFERENCES certificateauthority (id)
      ON UPDATE RESTRICT ON DELETE CASCADE,
  CONSTRAINT non_hosted_publisher_repository_unique_publisher_handle_idx UNIQUE (publisher_handle));

CREATE INDEX non_hosted_publisher_repository_ca_id_idx ON non_hosted_publisher_repository (ca_id);

COMMENT ON COLUMN non_hosted_publisher_repository.ca_id IS 'Non-hosted CA id';
COMMENT ON COLUMN non_hosted_publisher_repository.publisher_handle IS 'RFC8183 unique publisher_handle';
COMMENT ON COLUMN non_hosted_publisher_repository.publisher_request IS 'RFC8183 publisher_request XML message';
COMMENT ON COLUMN non_hosted_publisher_repository.repository_response IS 'RFC8183 repository_response XML message';
