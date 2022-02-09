BEGIN;

ALTER TABLE certificateauthority DROP CONSTRAINT hosted_ca_parent_id_check;

ALTER TABLE certificateauthority ADD CONSTRAINT hosted_ca_parent_id_check CHECK (
    CASE
        WHEN type::text IN ('HOSTED'::text, 'NONHOSTED'::text) THEN parent_id IS NOT NULL
        WHEN type::text = 'ALL_RESOURCES' THEN parent_id IS NULL
    END
);

-- force the DB to have at most one ALL_RESOURCES CA
CREATE UNIQUE INDEX certificateauthority_type_uniq_1 ON certificateauthority ("type") WHERE "type" = 'ALL_RESOURCES';

-- force the DB to have at most one ROOT ('production') CA
CREATE UNIQUE INDEX certificateauthority_type_uniq_2 ON certificateauthority ("type") WHERE "type" = 'ROOT';

COMMIT;

