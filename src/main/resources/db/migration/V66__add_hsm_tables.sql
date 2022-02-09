BEGIN;

CREATE TABLE hsm_key_store (
    id         BIGINT PRIMARY KEY,
    hmac       bytea,
    name       TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);


CREATE TABLE hsm_key (
    id BIGINT  PRIMARY KEY,
    key_blob   bytea NOT NULL,
    alias      TEXT NOT NULL,
    store_id   BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE TABLE hsm_certificate_chain (
    id bigint   PRIMARY KEY,
    content     BYTEA NOT NULL,
    chain_order INTEGER NOT NULL,
    key_id      BIGINT NOT NULL
);

ALTER TABLE hsm_certificate_chain
    ADD CONSTRAINT hsm_certificate_chain_key_id_fkey FOREIGN KEY (key_id) REFERENCES hsm_key(id);

ALTER TABLE hsm_key
    ADD CONSTRAINT hsm_key_store_id_fkey FOREIGN KEY (store_id) REFERENCES hsm_key_store(id);

CREATE UNIQUE INDEX idx_hsm_key_store_name ON hsm_key_store(name);
CREATE UNIQUE INDEX idx_hsm_key_alias_store_id ON hsm_key(alias, store_id);

COMMIT;
