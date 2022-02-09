DELETE FROM roaentity WHERE superseded = TRUE;
ALTER TABLE roaentity DROP COLUMN superseded;