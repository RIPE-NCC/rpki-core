package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.PublicKey;

public interface ResourceCertificateInformationAccessStrategy {

    X509CertificateInformationAccessDescriptor[] aiaForCertificate(IncomingResourceCertificate issuingCertificate);

    X509CertificateInformationAccessDescriptor[] siaForSignedObjectCertificate(KeyPairEntity issuingKeyPair, String extension, X500Principal certificateSubject, PublicKey certificatePublicKey);

    X500Principal caCertificateSubject(PublicKey subjectKey);

    X500Principal eeCertificateSubject(String signedObjectName, PublicKey subjectPublicKey, KeyPairEntity signingKeyPair);

    String caCertificateFilename(PublicKey subjectPublicKey);

    URI defaultCertificateRepositoryLocation(HostedCertificateAuthority ca, String resourceClassName);

    String roaFilename(OutgoingResourceCertificate endEntityCertificate);
}
