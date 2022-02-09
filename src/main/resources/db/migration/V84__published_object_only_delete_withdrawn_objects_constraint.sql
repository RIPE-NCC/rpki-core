CREATE OR REPLACE FUNCTION published_object_delete_withdrawn_only()
  RETURNS TRIGGER
  LANGUAGE PLPGSQL
  AS
$$
BEGIN
    IF OLD.status <> 'WITHDRAWN' THEN
        RAISE EXCEPTION 'Only published_object with status WITHDRAWN can be deleted';
    END IF;

    RETURN NULL;
END;
$$
;

CREATE CONSTRAINT TRIGGER published_object_delete_withdrawn_only
 AFTER DELETE ON published_object
   FOR EACH ROW EXECUTE FUNCTION published_object_delete_withdrawn_only();
