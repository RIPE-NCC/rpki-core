UPDATE commandaudit
   SET commandtype = 'ActivateHostedCertificateAuhtorityCommand'
 WHERE commandtype = 'ActivateCustomerCertificateAuthorityCommand';
