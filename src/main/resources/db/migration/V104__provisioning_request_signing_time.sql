CREATE TABLE provisioning_request_signing_time
(
    ca_id               BIGINT                   PRIMARY KEY REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    last_seen_signed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

COMMENT ON TABLE provisioning_request_signing_time
  IS 'Tracks last time of provisioning CMS request for delegated CAs to prevent replay attacks';

INSERT INTO provisioning_request_signing_time (ca_id, last_seen_signed_at)
SELECT id, last_message_seen_at
  FROM certificateauthority
 WHERE last_message_seen_at IS NOT NULL;
