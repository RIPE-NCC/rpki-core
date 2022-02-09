ALTER TABLE resourcecertificate
 ALTER COLUMN status DROP NOT NULL,
 DROP CONSTRAINT valid_status;

UPDATE resourcecertificate
   SET status = NULL
 WHERE type = 'INCOMING';

ALTER TABLE resourcecertificate
  ADD CONSTRAINT valid_status CHECK (
    CASE type
      WHEN 'INCOMING' THEN status IS NULL
      WHEN 'OUTGOING' THEN status IN ('CURRENT', 'REVOKED', 'EXPIRED')
    END
  );
