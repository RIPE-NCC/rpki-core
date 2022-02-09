-- There should be no older objects for a published URI that are not withdrawn.
UPDATE ta_published_object upd
   SET status = 'WITHDRAWN'
 WHERE status <> 'WITHDRAWN'
   AND upd.id < (SELECT MAX(id) FROM ta_published_object po WHERE upd.uri = po.uri AND po.status = 'PUBLISHED');

-- There should be no older objects for a to be published URI that have status PUBLISHED.
UPDATE ta_published_object upd
   SET status = 'TO_BE_WITHDRAWN'
 WHERE status = 'PUBLISHED'
   AND upd.id < (SELECT MAX(id) FROM ta_published_object po WHERE upd.uri = po.uri AND po.status = 'TO_BE_PUBLISHED');

CREATE UNIQUE INDEX idx_uniq_ta_published_object_location
    ON ta_published_object (uri)
 WHERE status IN ('PUBLISHED', 'TO_BE_WITHDRAWN');
