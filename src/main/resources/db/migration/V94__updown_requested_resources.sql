ALTER TABLE non_hosted_ca_public_key
  ADD COLUMN latest_provisioning_request_type TEXT CHECK (latest_provisioning_request_type IN ('issue', 'revoke')),
  ADD COLUMN req_resource_set_asn TEXT,
  ADD COLUMN req_resource_set_ipv4 TEXT,
  ADD COLUMN req_resource_set_ipv6 TEXT;

CREATE TABLE non_hosted_ca_public_key_requested_sia (
  public_key_entity_id BIGINT NOT NULL,
  index INTEGER NOT NULL,
  method TEXT NOT NULL,
  location TEXT NOT NULL,
  PRIMARY KEY (public_key_entity_id, index)
);
