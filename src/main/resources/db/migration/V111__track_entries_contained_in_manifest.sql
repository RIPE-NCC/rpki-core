ALTER TABLE published_object
 ADD COLUMN containing_manifest_id BIGINT REFERENCES manifestentity (id);

DROP INDEX published_object_issuing_key_pair_id;
CREATE INDEX published_object_issuing_key_pair_id
          ON published_object (issuing_key_pair_id, status)
     INCLUDE (containing_manifest_id, included_in_manifest);
CREATE INDEX published_object_containing_manifest_id
          ON published_object (containing_manifest_id);
