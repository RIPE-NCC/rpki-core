ALTER TABLE published_object
ADD COLUMN hash_sha256 bytea GENERATED ALWAYS AS (sha256(content)) STORED;

ALTER TABLE ta_published_object
ADD COLUMN hash_sha256 bytea GENERATED ALWAYS AS (sha256(content)) STORED;

