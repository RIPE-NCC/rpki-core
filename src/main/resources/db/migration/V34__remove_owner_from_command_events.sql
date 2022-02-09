update commandaudit
set command = regexp_replace(command,
          E'^\\s*<owner>.+?</owner>\n',
          E'',
          'gn')
where commandtype in ('ActivateCustomerCertificateAuthorityCommand', 'ActivateNonHostedCustomerCertificateAuthorityCommand');
