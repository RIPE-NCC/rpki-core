-- Use this script to initialize an existing certification db for use with 
-- flyway. Otherwise use the flyway "init" command

CREATE TABLE schema_version (
    version character varying(20) NOT NULL,
    description character varying(100),
    type character varying(10) NOT NULL,
    script character varying(200) NOT NULL,
    checksum integer,
    installed_by character varying(30) NOT NULL,
    installed_on timestamp without time zone DEFAULT now(),
    execution_time integer,
    state character varying(15) NOT NULL,
    current_version boolean NOT NULL
);

INSERT INTO schema_version
    (version, description, type, script, checksum, installed_by, installed_on, execution_time, state, current_version) 
VALUES
    (1, 'initial', 'INIT', 'initial', NULL, current_user, current_timestamp, 0, 'SUCCESS', 't');

ALTER TABLE ONLY schema_version
    ADD CONSTRAINT schema_version_primary_key PRIMARY KEY (version);

ALTER TABLE ONLY schema_version
    ADD CONSTRAINT schema_version_script_unique UNIQUE (script);
