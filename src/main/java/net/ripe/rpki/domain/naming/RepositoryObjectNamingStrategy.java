package net.ripe.rpki.domain.naming;

import com.google.common.collect.ImmutableSet;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;

import javax.security.auth.x500.X500Principal;
import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Set;

public interface RepositoryObjectNamingStrategy extends Serializable {

    String MANIFEST_FILE_EXTENSION = "mft";
    String ROA_FILE_EXTENSION = "roa";
    String CRL_FILE_EXTENSION = "crl";
    String CERTIFICATE_FILE_EXTENSION = "cer";

    Set<String> REPOSITORY_FILE_EXTENSIONS = ImmutableSet.of(
            CERTIFICATE_FILE_EXTENSION,
            CRL_FILE_EXTENSION,
            MANIFEST_FILE_EXTENSION,
            ROA_FILE_EXTENSION
    );

    String certificateFileName(PublicKey subjectPublicKey);

    String roaFileName(OutgoingResourceCertificate eeCertificate);

    String crlFileName(KeyPair keyPair);

    String signedObjectFileName(String extension, X500Principal certificateSubject, PublicKey certificatePublicKey);

    String manifestFileName(KeyPair keyPair);

    X500Principal caCertificateSubject(PublicKey subjectKey);

    X500Principal eeCertificateSubject(String signedObjectName, PublicKey subjectPublicKey, KeyPairEntity signingKeyPair);

}
