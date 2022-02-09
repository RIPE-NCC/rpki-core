-- Update serialized command XML to not include fully qualified package names.

update commandaudit
   set command = regexp_replace(command,
           E'net\\.ripe\\.certification\\.data\\.RoaPrefixData',
           E'RoaPrefix',
           'g')
 where command like '%net.ripe.certification.data.RoaPrefixData%';
update commandaudit
   set command = regexp_replace(command,
           E' class="net\\.ripe\\.rpki\\.server\\.api\\.dto\\.IncomingResourceCertificateData"',
           E'',
           'g')
 where command like '%net.ripe.rpki.server.api.dto.IncomingResourceCertificateData%';
update commandaudit
   set command = regexp_replace(command,
           E' class="net\\.ripe\\.certification\\.data\\.IncomingResourceCertificateData"',
           E'',
           'g')
 where command like '%net.ripe.certification.data.IncomingResourceCertificateData%';
