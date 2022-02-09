package net.ripe.rpki.domain.naming;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.KeyPairEntityKeyInfo;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import org.apache.commons.lang.Validate;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;

public class UuidRepositoryObjectNamingStrategy implements RepositoryObjectNamingStrategy {


    private static final long serialVersionUID = 1L;



    @Override
    public String certificateFileName(PublicKey subjectPublicKey) {
        return getDashSafeEncodedPublicKeyHash(subjectPublicKey) + "." + CERTIFICATE_FILE_EXTENSION;
    }

    @Override
    public String crlFileName(KeyPairEntityKeyInfo keyInfo) {
        return getDashSafeEncodedPublicKeyHash(keyInfo.getKeyPair().getPublic()) + "." + CRL_FILE_EXTENSION;
    }

    @Override
    public String roaFileName(OutgoingResourceCertificate eeCertificate) {
        return getDashSafeEncodedPublicKeyHash(eeCertificate.getSubjectPublicKey()) + "." + ROA_FILE_EXTENSION;
    }

    @Override
    public String signedObjectFileName(String extension, X500Principal certificateSubject, PublicKey certificatePublicKey) {
        Validate.isTrue(REPOSITORY_FILE_EXTENSIONS.contains(extension), "illegal extension '" + extension + "'");
        return getDashSafeEncodedPublicKeyHash(certificatePublicKey) + "." + extension;
    }

    @Override
    public String manifestFileName(KeyPairEntityKeyInfo keyInfo) {
        return getDashSafeEncodedPublicKeyHash(keyInfo.getKeyPair().getPublic()) + "." + MANIFEST_FILE_EXTENSION;
    }

    @Override
    public X500Principal caCertificateSubject(PublicKey subjectKey) {
        return getCertificateSubject(subjectKey);
    }

    @Override
    public X500Principal eeCertificateSubject(String signedObjectName, PublicKey subjectPublicKey, KeyPairEntity signingKeyPair) {
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
