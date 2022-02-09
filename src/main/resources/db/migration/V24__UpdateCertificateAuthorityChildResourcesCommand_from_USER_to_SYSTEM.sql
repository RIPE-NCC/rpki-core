-- Update resources of the UpdateCertificateAuthorityResourcesCommand to match the new ResourceClassMap.

update commandaudit
   set command = regexp_replace(command,
           E'<commandGroup>USER</commandGroup>',
           E'<commandGroup>SYSTEM</commandGroup>',
           'g')
 where commandtype = 'UpdateCertificateAuthorityChildResourcesCommand';
