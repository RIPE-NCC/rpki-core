-- Update serialized command XML to not include fully qualified package names.

update eventaudit
   set event = regexp_replace(event,
           E'commons\\.x509cert\\.X509CertificateInformationAccessDescriptor',
           E'X509CertificateInformationAccessDescriptor',
           'g');
