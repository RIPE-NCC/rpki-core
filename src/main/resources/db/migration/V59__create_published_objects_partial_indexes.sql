BEGIN;

--DROP INDEX IF EXISTS idx_part_published_object;
--DROP INDEX IF EXISTS idx_part2_published_object;

--CREATE INDEX idx_part_published_object ON published_object(issuing_key_pair_id) WHERE status IN ('TO_BE_PUBLISHED', 'PUBLISHED');
--CREATE INDEX idx_part2_published_object ON published_object(issuing_key_pair_id) WHERE status IN ('TO_BE_PUBLISHED', 'PUBLISHED', 'TO_BE_WITHDRAWN');

COMMIT;
