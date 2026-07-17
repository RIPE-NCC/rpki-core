package net.ripe.rpki.domain;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.PublicKey;

public interface ResourceCertificateInformationAccessStrategy {

    X509CertificateInformationAccessDescriptor[] aiaForCertificate(IncomingResourceCertificate issuingCertificate);

    X509CertificateInformationAccessDescriptor[] siaForSignedObjectCertificate(KeyPairEntity issuingKeyPair, String extension, X500Principal certificateSubject, PublicKey certificatePublicKey);

    X500Principal caCertificateSubject(PublicKey subjectKey);

    X500Principal eeCertificateSubject(PublicKey subjectPublicKey);

    String caCertificateFilename(PublicKey subjectPublicKey);

    URI defaultCertificateRepositoryLocation(ManagedCertificateAuthority ca, String resourceClassName);

    String roaFilename(OutgoingResourceCertificate eeCertificate);

    String aspaFilename(OutgoingResourceCertificate eeCertificate);

    String bgpSecFilename(X509ResourceCertificate eeCertificate, Asn asn, Long routerId);

    X500Principal bgpSecCertificateSubject(Asn asn, Long routerId);
}
