package net.ripe.rpki.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.GenericRpkiSignedObjectParser;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.commons.validation.ValidationResult;
import org.joda.time.Instant;

import java.net.URI;
import java.util.Base64;

@Slf4j
@UtilityClass
public class PublishedObjectUtil {

    // FIXME: Should be moved into rpki-commons after we use >=1.35 in core because this is a port of code present in commons test-cases and in rsyncit.
    public static Instant getFileCreationTime(URI uri, byte[] decoded) {
        var objectUri = uri.toString();
        final RepositoryObjectType objectType = RepositoryObjectType.parse(objectUri);
        try {
            switch (objectType) {
                case Manifest:
                case Aspa:
                case Roa:
                case Gbr:
                    var signedObjectParser = new GenericRpkiSignedObjectParser();

                    signedObjectParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    return Instant.ofEpochMilli(signedObjectParser.getSigningTime().getMillis());
                case Certificate:
                    X509ResourceCertificateParser x509CertificateParser = new X509ResourceCertificateParser();
                    x509CertificateParser.parse(ValidationResult.withLocation(objectUri), decoded);
                    final var cert = x509CertificateParser.getCertificate().getCertificate();
                    return Instant.ofEpochMilli(cert.getNotBefore().getTime());
                case Crl:
                    var x509Crl = X509Crl.parseDerEncoded(decoded, ValidationResult.withLocation(objectUri));
                    var crl = x509Crl.getCrl();
                    return Instant.ofEpochMilli(crl.getThisUpdate().getTime());
                case Unknown:
                    log.error("Unknown object type for object url = {}");
                    return Instant.now();
            }
        } catch (Exception e) {
            log.error("Could not parse the object url = {}, body = {} :", objectUri, Base64.getEncoder().encodeToString(decoded));
        }
        return Instant.now();
    }
}
