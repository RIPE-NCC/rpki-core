DROP INDEX resourcecertificate_subject_keypair_id_fk;

ALTER TABLE resourcecertificate
  ADD CONSTRAINT valid_type CHECK (type IN ('INCOMING', 'OUTGOING'));

DELETE FROM resourcecertificate
 WHERE type = 'INCOMING'
   AND status NOT IN ('CURRENT');

ALTER TABLE resourcecertificate
  ADD CONSTRAINT valid_status CHECK (
    CASE type
      WHEN 'INCOMING' THEN status IN ('CURRENT')
      WHEN 'OUTGOING' THEN status IN ('CURRENT', 'OLD', 'REVOKED', 'EXPIRED')
    END
  );

CREATE UNIQUE INDEX resourcecertificate_subject_keypair_id_fk ON resourcecertificate (subject_keypair_id);
