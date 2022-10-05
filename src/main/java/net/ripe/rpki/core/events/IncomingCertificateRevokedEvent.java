package net.ripe.rpki.core.events;

import lombok.EqualsAndHashCode;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.server.api.commands.CommandContext;

import java.math.BigInteger;
import java.net.URI;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
public class IncomingCertificateRevokedEvent extends CertificateAuthorityEvent {
    private final CertificateRevocationResponse response;

    private final Optional<URI> publicationUri;
    private final Optional<X509ResourceCertificate> incomingCertificate;

    public IncomingCertificateRevokedEvent(final VersionedId certificateAuthorityId, CertificateRevocationResponse response, Optional<URI> publicationUri, Optional<X509ResourceCertificate> incomingCertificate) {
        super(certificateAuthorityId);
        this.response = response;
        this.publicationUri = publicationUri;
        this.incomingCertificate = incomingCertificate;
    }

    @Override
    public void accept(CertificateAuthorityEventVisitor visitor, CommandContext recording) {
        visitor.visitIncomingCertificateRevokedEvent(this, recording);
    }

    @Override
    public String toString() {
        // This string representation is stored in the command audit table and shown to the user
        return String.format(
                "Certificate for CA was revoked [uri=%s, serial=%s, public key hash=%s]",
                publicationUri.map(URI::toString).orElse("n/a"),
                incomingCertificate.map(X509ResourceCertificate::getSerialNumber).map(BigInteger::toString).orElse("n/a"),
                KeyPairUtil.getAsciiHexEncodedPublicKeyHash(response.getSubjectPublicKey())
        );
    }
}
