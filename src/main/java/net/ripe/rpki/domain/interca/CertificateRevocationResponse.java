package net.ripe.rpki.domain.interca;

import lombok.Value;

import java.security.PublicKey;

import static java.util.Objects.requireNonNull;

/**
 * This class does not represent any RFC and used for internal request-response modelling
 * of hosted CAs parent-child interaction and represents the response to CertificateRevocationRequest.
 */
@Value
public class CertificateRevocationResponse {

    PublicKey subjectPublicKey;

    public CertificateRevocationResponse(PublicKey subjectPublicKey) {
        this.subjectPublicKey = requireNonNull(subjectPublicKey, "subjectPublicKey is required");
    }

}
