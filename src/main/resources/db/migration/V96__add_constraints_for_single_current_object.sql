ALTER TABLE keypair ADD CONSTRAINT
    unique_current EXCLUDE (ca_id with =) WHERE (status = 'CURRENT')
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE resourcecertificate ADD CONSTRAINT
    unique_current_subject_key EXCLUDE (subject_public_key with =) WHERE (type = 'OUTGOING' AND status = 'CURRENT' AND NOT embedded)
    DEFERRABLE INITIALLY DEFERRED;