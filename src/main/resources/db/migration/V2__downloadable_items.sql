CREATE TABLE downloadable_item (
    id bigint NOT NULL,
    name character varying NOT NULL,
    type character varying NOT NULL,
    content_type character varying NOT NULL,
    encoded bytea NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    CONSTRAINT downloadable_item_pkey PRIMARY KEY (id)
);
