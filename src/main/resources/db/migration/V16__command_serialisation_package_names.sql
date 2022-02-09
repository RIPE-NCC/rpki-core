-- Update serialized command XML to not include fully qualified package names.

update commandaudit
   set command = regexp_replace(command,
           E'commons\\.x509cert\\.X509ResourceCertificate',
           E'X509ResourceCertificate',
           'g')
 where command like '%commons.x509cert.X509ResourceCertificate%';
update commandaudit
   set command = regexp_replace(command,
           E'commons\\.crl\\.X509Crl',
           E'X509Crl',
           'g')
 where command like '%commons.crl.X509Crl%';
update commandaudit
   set command = regexp_replace(command,
           E'commons\\.cms\\.manifest\\.ManifestCms',
           E'ManifestCms',
           'g')
 where command like '%commons.cms.manifest.ManifestCms%';
update commandaudit
   set command = regexp_replace(command,
           E'commons\\.cms\\.roa\\.RoaCms',
           E'RoaCms',
           'g')
 where command like '%commons.cms.roa.RoaCms%';
update commandaudit
   set command = regexp_replace(command,
           E'commons\\.validation\\.roa\\.RouteValidityState',
           E'RouteValidityState',
           'g')
 where command like '%commons.validation.roa.RouteValidityState%';
update commandaudit
   set command = regexp_replace(command,
           E'net\\.ripe\\.rpki\\.RouteValidityState',
           E'RouteValidityState',
           'g')
 where command like '%net.ripe.rpki.RouteValidityState%';
update commandaudit
   set command = regexp_replace(command,
           E'net.ripe.rpki.server.api.dto.KeyPairStatus',
           E'KeyPairStatus',
           'g')
 where command like '%net.ripe.rpki.server.api.dto.KeyPairStatus%';
