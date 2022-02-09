CREATE TABLE roaconfiguration (
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  id bigint not null primary key,
  certificateauthority_id bigint not null);
ALTER TABLE roaconfiguration
    ADD CONSTRAINT roaconfiguration_unique_per_ca UNIQUE (certificateauthority_id, id);
ALTER TABLE roaconfiguration
    ADD CONSTRAINT roaconfiguration_ca_id_fkey FOREIGN KEY (certificateauthority_id) REFERENCES certificateauthority (id) ON UPDATE RESTRICT ON DELETE CASCADE;

CREATE TABLE roaconfiguration_prefixes (
    roaconfiguration_id BIGINT NOT NULL,
    asn numeric NOT NULL,
    prefix_type_id INTEGER NOT NULL,
    prefix_start NUMERIC NOT NULL,
    prefix_end NUMERIC NOT NULL,
    maximum_length INTEGER);
ALTER TABLE roaconfiguration_prefixes
    ADD CONSTRAINT roaconfiguration_ignored_pkey PRIMARY KEY (roaconfiguration_id, asn, prefix_type_id, prefix_start, prefix_end);
ALTER TABLE roaconfiguration_prefixes
    ADD CONSTRAINT roaconfiguration_ca_id_fkey FOREIGN KEY (roaconfiguration_id) REFERENCES roaconfiguration(id) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE roaconfiguration_prefixes
    ADD CONSTRAINT roaconfiguration_prefix_type_fkey FOREIGN KEY (prefix_type_id) REFERENCES ip_resource_type(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION migrate() RETURNS void AS $$
DECLARE
  config_id bigint;
  ca_id bigint;
  roa_prefix RECORD;
BEGIN
  FOR ca_id IN SELECT id FROM certificateauthority LOOP
    SELECT NEXTVAL('seq_all') INTO config_id;
    INSERT INTO roaconfiguration VALUES (now(), now(), config_id, ca_id);
    FOR roa_prefix IN
      SELECT rs.asn, rsp.resource_type_id, rsp.resource_start, rsp.resource_end, MAX(rsp.maximum_length) AS maximum_length
        FROM roaspecification rs INNER JOIN roaspecification_prefixes rsp ON rs.id = rsp.roaspecification_id
       WHERE (rs.validity_not_after IS NULL OR now() <= rs.validity_not_after)
         AND (rs.validity_not_before IS NULL OR now() >= rs.validity_not_before)
         AND rs.certificateauthority_id = ca_id
       GROUP BY 1, 2, 3, 4
    LOOP
      INSERT INTO roaconfiguration_prefixes
           VALUES (config_id, roa_prefix.asn, roa_prefix.resource_type_id, roa_prefix.resource_start, roa_prefix.resource_end, roa_prefix.maximum_length);
    END LOOP;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT migrate();

DROP FUNCTION migrate();


ALTER TABLE roaentity DROP COLUMN roaspecification_id;

DROP TABLE roaspecification_prefixes;
DROP TABLE roaspecification;
