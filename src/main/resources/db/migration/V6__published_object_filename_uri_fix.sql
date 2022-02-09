-- V4 migration script inserted wrong data in PUBLISHED_OBJECT.FILENAME column
-- This script fixes that and removes the (unnecessary) URI column.

UPDATE published_object
   SET filename = regexp_replace(uri, '^.*?([^/]+)$', E'\\1')
 WHERE filename LIKE '%/%';
ALTER TABLE published_object DROP COLUMN uri;
