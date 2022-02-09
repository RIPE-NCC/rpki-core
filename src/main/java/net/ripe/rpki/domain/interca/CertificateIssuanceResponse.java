package net.ripe.rpki.domain.interca;

import lombok.Value;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.ta.domain.response.SigningResponse;

import java.net.URI;

import static java.util.Objects.requireNonNull;

/**
 * This class does not represent any RFC and used for internal request-response modelling
 * of hosted CAs parent-child interaction and represents the response to CertificateIssuanceRequest.
 */
@Value
public class CertificateIssuanceResponse {

    X509ResourceCertificate certificate;
    URI publicationUri;

    public static CertificateIssuanceResponse fromTaSigningResponse(SigningResponse response) {
        return new CertificateIssuanceResponse(response.getCertificate(), response.getPublicationUri());
    }

    public CertificateIssuanceResponse(X509ResourceCertificate certificate, URI publicationUri) {
        this.certificate = requireNonNull(certificate, "certificate is required");
        this.publicationUri = requireNonNull(publicationUri, "publicationUri is required");
    }

}
