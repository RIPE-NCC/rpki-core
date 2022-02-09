-- Turn the following commands into SYSTEM commands

UPDATE commandaudit
   SET command = regexp_replace(command,
                                E'<commandGroup>USER</commandGroup>',
                                E'<commandGroup>SYSTEM</commandGroup>',
                                'g')
 WHERE commandtype IN ('ActivatePendingKeypairCommand',
                       'AutoKeyRolloverChildCaCommand',
                       'GenerateKeyPairCommand',
                       'RevokeKeyPairCommand',
                       'UpdateAllIncomingResourceCertificatesCommand',
                       'UpdateCertificateAuthorityResourcesCommand');
