-- Update resources of the UpdateCertificateAuthorityResourcesCommand to match the new ResourceClassMap.

update commandaudit
   set command = regexp_replace(command,
           E'<resourceSet>(.+?)</resourceSet>',
           E'<resourceClasses>\n    <class name="RIPE">\\1</class>\n  </resourceClasses>',
           'g')
 where commandtype = 'UpdateCertificateAuthorityResourcesCommand';


update commandaudit
set command = regexp_replace(command,
           E'<resources>(.+?)</resources>',
           E'<resourceClasses>\n    <class name="RIPE">\\1</class>\n  </resourceClasses>',
           'g')
where commandtype in ('ActivateCustomerCertificateAuthorityCommand', 'ActivateNonHostedCustomerCertificateAuthorityCommand');
