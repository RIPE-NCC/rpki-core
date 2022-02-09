CREATE TABLE ca_child_certifiable_resources (
  ca_child_id bigint not null,
  class_name text not null,
  resources text not null,
  PRIMARY KEY (ca_child_id, class_name));

INSERT INTO ca_child_certifiable_resources (ca_child_id, class_name, resources)
SELECT id, 'RIPE', certifiable_resources
  FROM ca_child;

ALTER TABLE ca_child DROP COLUMN certifiable_resources;
