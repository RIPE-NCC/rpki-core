-- Replaces old V4__published_object_migration.java and doesn't
-- do the data migration. Fortunately, this is only used for
-- clean installs since the production system has been upgraded
-- a long time ago. Therefore, there is no data in the tables yet
-- so we can stick to DDL migrations.

-- Published objects
CREATE TABLE published_object (
    id bigint not null,
    version int not null,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    status text not null,
    issuing_key_pair_id bigint not null REFERENCES keypair (id),
    filename text not null,
    uri text not null,
    content bytea not null,
    included_in_manifest boolean not null,
    primary key (id));

CREATE INDEX published_object_uri ON published_object (uri);
CREATE INDEX published_object_issuing_key_pair_id ON published_object (issuing_key_pair_id);

ALTER TABLE roaentity ADD COLUMN published_object_id BIGINT;
ALTER TABLE crlentity ADD COLUMN published_object_id BIGINT;
ALTER TABLE manifestentity ADD COLUMN published_object_id BIGINT;
ALTER TABLE resourcecertificate ADD COLUMN published_object_id BIGINT;

ALTER TABLE keypair ADD COLUMN crl_filename TEXT;
ALTER TABLE keypair ADD COLUMN manifest_filename TEXT;

CREATE INDEX roaentity_published_object_id ON roaentity (published_object_id);
CREATE INDEX crlentity_published_object_id ON crlentity (published_object_id);
CREATE INDEX manifestentity_published_object_id ON manifestentity (published_object_id);
CREATE INDEX resourcecertificate_published_object_id ON resourcecertificate (published_object_id);

ALTER TABLE roaentity ADD CONSTRAINT roaentity_published_object_fk FOREIGN KEY (published_object_id) REFERENCES published_object (id);
ALTER TABLE crlentity ADD CONSTRAINT crlentity_published_object_fk FOREIGN KEY (published_object_id) REFERENCES published_object (id);
ALTER TABLE manifestentity ADD CONSTRAINT manifestentity_published_object_fk FOREIGN KEY (published_object_id) REFERENCES published_object (id);
ALTER TABLE resourcecertificate ADD CONSTRAINT resourcecertificate_published_object_fk FOREIGN KEY (published_object_id) REFERENCES published_object (id);

ALTER TABLE roaentity ALTER published_object_id SET NOT NULL;
ALTER TABLE crlentity ALTER published_object_id SET NOT NULL;
ALTER TABLE manifestentity ALTER published_object_id SET NOT NULL;

ALTER TABLE keypair ADD CONSTRAINT keypair_crl_filename CHECK (
    CASE
      WHEN type = 'HOSTED' THEN crl_filename IS NOT NULL
      ELSE crl_filename IS NULL
    END);
ALTER TABLE keypair ADD CONSTRAINT keypair_manifest_filename CHECK (
    CASE
      WHEN type = 'HOSTED' THEN manifest_filename IS NOT NULL
      ELSE manifest_filename IS NULL
    END);

ALTER TABLE roaentity DROP COLUMN roacms;
ALTER TABLE crlentity DROP COLUMN encoded;
ALTER TABLE manifestentity DROP COLUMN manifestcms;

ALTER TABLE resourcecertificate ADD CONSTRAINT resourcecertificate_published_object_id CHECK (
    CASE
      WHEN type = 'OUTGOING' AND NOT embedded THEN published_object_id IS NOT NULL
      ELSE published_object_id IS NULL
    END);

-- publication server
CREATE TABLE publicationobjectentity (
    id BIGINT NOT NULL PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    uri TEXT NOT NULL,
    contents BYTEA NOT NULL,
    hash BYTEA NOT NULL UNIQUE,
    published BOOLEAN NOT NULL);

CREATE INDEX poe_uri_idx ON publicationobjectentity(uri, created_at) where published;

-- Locking is now done using SELECT ... FOR UPDATE on the certificate authority.
DROP TABLE ca_lock;

-- Alert subscriptions
ALTER TABLE alert_subscription ADD COLUMN route_validity_states text NOT NULL DEFAULT 'INVALID,UNKNOWN';
ALTER TABLE alert_subscription ALTER COLUMN route_validity_states DROP DEFAULT;

-- ROA configuration
ALTER TABLE roaentity ALTER roaspecification_id DROP NOT NULL;
