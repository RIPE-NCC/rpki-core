ALTER TABLE keypair ADD COLUMN certificate_repository_location char varying(2000);

UPDATE keypair kp
    SET certificate_repository_location = '${online.repository.uri}' || substr(ca.uuid, 1, 2) || '/' || substr(ca.uuid, 3) || '/1/'
    FROM certificateauthority ca
    WHERE kp.type = 'HOSTED'
        AND kp.certificate_repository_location IS NULL
        AND kp.certificateauthority_id = ca.id;

ALTER TABLE keypair ADD CONSTRAINT hosted_type_implies_certificate_repository_location CHECK (type <> 'HOSTED' OR certificate_repository_location IS NOT NULL);