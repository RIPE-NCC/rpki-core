CREATE INDEX idx_active_published_objects
    ON published_object (status)
 WHERE status IN ('TO_BE_PUBLISHED', 'PUBLISHED', 'TO_BE_WITHDRAWN');
