DROP INDEX IF EXISTS idx_uniq_published_object_filename_to_be_published;
CREATE UNIQUE INDEX idx_uniq_published_object_location
    ON published_object (directory, filename)
 WHERE status IN ('PUBLISHED', 'TO_BE_WITHDRAWN');
