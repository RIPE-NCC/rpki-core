BEGIN;

CREATE TABLE ta_published_object (
    id bigint PRIMARY KEY,
    created_at timestamp without time zone DEFAULT NOW(),
    updated_at timestamp without time zone DEFAULT NOW(),
    content bytea NOT NULL,
    status text NOT NULL,
    uri text NOT NULL,
    version integer NOT NULL
);

DROP INDEX IF EXISTS ixd_ta_published_object_status;
CREATE INDEX ixd_ta_published_object_status ON ta_published_object(status);

COMMIT;
