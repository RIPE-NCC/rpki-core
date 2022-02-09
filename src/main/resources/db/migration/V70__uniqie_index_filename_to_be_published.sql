BEGIN;

CREATE UNIQUE INDEX idx_uniq_published_object_filename_to_be_published ON published_object(filename) WHERE status = 'TO_BE_PUBLISHED';

COMMIT;

