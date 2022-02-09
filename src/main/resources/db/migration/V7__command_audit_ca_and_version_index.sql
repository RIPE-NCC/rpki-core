DROP INDEX commandaudit_ca_id;
CREATE INDEX commandaudit_ca_and_version_idx ON commandaudit (ca_id, ca_version);
