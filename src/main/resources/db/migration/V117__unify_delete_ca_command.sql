WITH deleted AS (SELECT ca_id, executiontime
                   FROM commandaudit
                  WHERE commandtype = 'DeleteNonHostedCertificateAuthorityCommand')
UPDATE commandaudit
   SET deleted_at = deleted.executiontime
  FROM deleted
 WHERE commandaudit.ca_id = deleted.ca_id;

UPDATE commandaudit
   SET commandtype = 'DeleteCertificateAuthorityCommand'
 WHERE commandtype = 'DeleteNonHostedCertificateAuthorityCommand';
