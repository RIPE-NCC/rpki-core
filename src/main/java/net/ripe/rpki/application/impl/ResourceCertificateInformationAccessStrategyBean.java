package net.ripe.rpki.application.impl;

import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateInformationAccessStrategy;
import net.ripe.rpki.domain.naming.RepositoryObjectNamingStrategy;
import net.ripe.rpki.domain.naming.UuidRepositoryObjectNamingStrategy;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class ResourceCertificateInformationAccessStrategyBean implements ResourceCertificateInformationAccessStrategy {

    public static final String DEFAULT_PUBLICATION_SUBDIRECTORY = "1";
    public static final String ACA_PUBLICATION_SUBDIRECTORY = "aca";

    private final RepositoryObjectNamingStrategy strategy = new UuidRepositoryObjectNamingStrategy();

    @Override
    public X509CertificateInformationAccessDescriptor[] aiaForCertificate(IncomingResourceCertificate issuingCertificate) {
        Validate.notNull(issuingCertificate.getPublicationUri(), "issuing certificate's publication URI is null");
        return new X509CertificateInformationAccessDescriptor[] {
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_CA_CA_ISSUERS, issuingCertificate.getPublicationUri())
        };
    }

    @Override
    public X509CertificateInformationAccessDescriptor[] siaForSignedObjectCertificate(KeyPairEntity issuingKeyPair, String extension,
            X500Principal certificateSubject, PublicKey certificatePublicKey) {
        String fileName = strategy.signedObjectFileName(extension, certificateSubject, certificatePublicKey);
        URI signedObjectLocation = issuingKeyPair.getCertificateRepositoryLocation().resolve(fileName);
        return new X509CertificateInformationAccessDescriptor[] {
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT, signedObjectLocation),
        };
    }

    @Override
    public X500Principal caCertificateSubject(PublicKey subjectKey) {
        return strategy.caCertificateSubject(subjectKey);
    }

    @Override
    public X500Principal eeCertificateSubject(String signedObjectName, PublicKey subjectPublicKey, KeyPairEntity signingKeyPair) {
        return strategy.eeCertificateSubject(signedObjectName, subjectPublicKey, signingKeyPair);
    }

    @Override
    public String caCertificateFilename(PublicKey subjectPublicKey) {
        return strategy.certificateFileName(subjectPublicKey);
    }

    @Override
    public String roaFilename(OutgoingResourceCertificate endEntityCertificate) {
        return strategy.roaFileName(endEntityCertificate);
    }

    @Override
    public URI defaultCertificateRepositoryLocation(HostedCertificateAuthority ca, String resourceClassName) {
        if (ca.isAllResourcesCa()) {
            return URI.create(ACA_PUBLICATION_SUBDIRECTORY + '/');
        } else if (ca.isProductionCa()) {
            return URI.create(resourceClassName + '/');
        } else {
            String relativePath = relativePathName(ca);
            return URI.create(resourceClassName + '/' + relativePath + '/');
        }
    }

    private List<String> buildCaPathElements(CertificateAuthority ca) {
        final List<String> result = new ArrayList<>();
        String s = ca.getUuid().toString();
        result.add(s.substring(0, 2));
        result.add(s.substring(2));
        return result;
    }

    private String relativePathName(CertificateAuthority ca) {
        List<String> pathElements = buildCaPathElements(ca);
        pathElements.add(DEFAULT_PUBLICATION_SUBDIRECTORY);
        return StringUtils.join(pathElements, '/');
    }
}
