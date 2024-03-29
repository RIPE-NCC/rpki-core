package net.ripe.rpki.domain.naming;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import org.apache.commons.lang.Validate;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.PublicKey;

public class UuidRepositoryObjectNamingStrategy implements RepositoryObjectNamingStrategy {

    @Override
    public String certificateFileName(PublicKey subjectPublicKey) {
        return getDashSafeEncodedPublicKeyHash(subjectPublicKey) + "." + CERTIFICATE_FILE_EXTENSION;
    }

    @Override
    public String crlFileName(KeyPair keyPair) {
        return getDashSafeEncodedPublicKeyHash(keyPair.getPublic()) + "." + CRL_FILE_EXTENSION;
    }

    @Override
    public String roaFileName(OutgoingResourceCertificate eeCertificate) {
        return getDashSafeEncodedPublicKeyHash(eeCertificate.getSubjectPublicKey()) + "." + ROA_FILE_EXTENSION;
    }

    @Override
    public String aspaFileName(OutgoingResourceCertificate eeCertificate) {
        return getDashSafeEncodedPublicKeyHash(eeCertificate.getSubjectPublicKey()) + "." + ASPA_FILE_EXTENSION;
    }

    @Override
    public String signedObjectFileName(String extension, X500Principal certificateSubject, PublicKey certificatePublicKey) {
        Validate.isTrue(REPOSITORY_FILE_EXTENSIONS.contains(extension), "illegal extension '" + extension + "'");
        return getDashSafeEncodedPublicKeyHash(certificatePublicKey) + "." + extension;
    }

    @Override
    public String manifestFileName(KeyPair keyPair) {
        return getDashSafeEncodedPublicKeyHash(keyPair.getPublic()) + "." + MANIFEST_FILE_EXTENSION;
    }

    @Override
    public X500Principal caCertificateSubject(PublicKey subjectKey) {
        return getCertificateSubject(subjectKey);
    }

    @Override
    public X500Principal eeCertificateSubject(PublicKey subjectPublicKey) {
        return getCertificateSubject(subjectPublicKey);
    }

    public X500Principal getCertificateSubject(PublicKey publicKey) {
        return new X500Principal("CN=" + KeyPairUtil.getAsciiHexEncodedPublicKeyHash(publicKey));
    }

    public static String getDashSafeEncodedPublicKeyHash(PublicKey subjectPublicKey) {
        String encodedKeyHash = KeyPairUtil.getEncodedKeyIdentifier(subjectPublicKey);
        if (encodedKeyHash.startsWith("-")) {
            return "1" + encodedKeyHash;
        }
        return encodedKeyHash;
    }
}
