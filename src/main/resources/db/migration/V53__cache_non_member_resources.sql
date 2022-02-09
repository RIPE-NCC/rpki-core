ALTER TABLE resource_cache ALTER COLUMN membershipid TYPE TEXT;
ALTER TABLE resource_cache RENAME COLUMN membershipid TO name;
UPDATE resource_cache SET name = 'CN=' || name;
