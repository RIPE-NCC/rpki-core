BEGIN;

ALTER TABLE hsm_certificate_chain DROP CONSTRAINT hsm_certificate_chain_key_id_fkey;

ALTER TABLE hsm_key DROP CONSTRAINT hsm_key_store_id_fkey;

ALTER TABLE hsm_certificate_chain
    ADD CONSTRAINT hsm_certificate_chain_key_id_fkey FOREIGN KEY (key_id) REFERENCES hsm_key(id) ON DELETE CASCADE;

ALTER TABLE hsm_key
    ADD CONSTRAINT hsm_key_store_id_fkey FOREIGN KEY (store_id) REFERENCES hsm_key_store(id) ON DELETE CASCADE;


COMMIT;
