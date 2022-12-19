UPDATE commandaudit c1
   SET deleted_at = COALESCE((SELECT deleted_at FROM commandaudit c2 WHERE c1.ca_id = c2.ca_id AND deleted_at IS NOT NULL LIMIT 1), executiontime)
 WHERE commandtype = 'DeleteCertificateAuthorityCommand'
   AND deleted_at IS NULL;
