CREATE TABLE keypair_statushistory (
  keypair_id BIGINT NOT NULL,
  status CHARACTER VARYING(10) NOT NULL,
  changed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  PRIMARY KEY (keypair_id, status));

ALTER TABLE keypair_statushistory ADD CONSTRAINT keypair_statushistory_fk FOREIGN KEY (keypair_id) REFERENCES keypair (id) ON UPDATE RESTRICT ON DELETE CASCADE;

INSERT INTO keypair_statushistory SELECT kp.id, 'NEW', kp.created_at FROM keypair kp;
INSERT INTO keypair_statushistory SELECT kp.id, 'PENDING', (select min(rc.created_at) from resourcecertificate rc where rc.type = 'INCOMING' and rc.subject_keypair_id = kp.id) FROM keypair kp WHERE status IN ('PENDING', 'CURRENT', 'OLD', 'MUSTREVOKE', 'REVOKED');
INSERT INTO keypair_statushistory SELECT kp.id, 'CURRENT', kp.updated_at FROM keypair kp WHERE kp.status IN ('CURRENT', 'OLD', 'MUSTREVOKE', 'REVOKED');
INSERT INTO keypair_statushistory SELECT kp.id, 'OLD', kp.updated_at FROM keypair kp WHERE kp.status IN ('OLD', 'MUSTREVOKE', 'REVOKED');
INSERT INTO keypair_statushistory SELECT kp.id, 'MUSTREVOKE', kp.updated_at FROM keypair kp WHERE kp.status IN ('MUSTREVOKE', 'REVOKED');
INSERT INTO keypair_statushistory SELECT kp.id, 'REVOKED', kp.updated_at FROM keypair kp WHERE kp.status IN ('REVOKED');
