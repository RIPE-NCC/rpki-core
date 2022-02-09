BEGIN;

DROP INDEX IF EXISTS idx_uniq_published_object_filename_to_be_published;
CREATE UNIQUE INDEX idx_uniq_published_object_filename_to_be_published ON published_object(directory, filename) WHERE status = 'TO_BE_PUBLISHED';

COMMIT;
