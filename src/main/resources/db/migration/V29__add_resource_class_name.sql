alter table ca_resource_class add column name character varying(2000);
update ca_resource_class set name = 'RIPE';
alter table ca_resource_class alter column name SET NOT NULL;