package net.ripe.rpki.core.events;

import lombok.*;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CommandContext;

/**
 * Certificate authority got a new, active, incoming certificate.
 */
@EqualsAndHashCode(callSuper = true)
public class IncomingCertificateUpdatedEvent extends CertificateAuthorityEvent {
    @Getter
    private final X509ResourceCertificate incomingCertificate;

    public IncomingCertificateUpdatedEvent(final VersionedId certificateAuthorityId, final X509ResourceCertificate incomingCertificate) {
        super(certificateAuthorityId);
        this.incomingCertificate = incomingCertificate;
    }

    @Override
    public void accept(CertificateAuthorityEventVisitor visitor, CommandContext recording) {
        visitor.visitIncomingCertificateUpdatedEvent(this, recording);
    }

    @Override
    public String toString() {
        String displayResources = incomingCertificate.resources().toString();
        // prevent extremely long lines when resource certificate is updated. The full resources are in logfiles.
        //
        // This limit is ~2x what is needed for all CAs except the production CA. When truncating we log a short prefix.
        if (displayResources.length() > 16384) {
            displayResources = displayResources.substring(0, 1024) + " [truncated]...";
        }

        // This string representation is stored in the command audit table and shown to the user
        return String.format(
            "Incoming resource certificate updated [public key hash=%s, resources=%s, serial number=%s, not valid before=%s, not valid after=%s]",
            KeyPairUtil.getAsciiHexEncodedPublicKeyHash(incomingCertificate.getPublicKey()),
            displayResources,
            incomingCertificate.getSerialNumber(),
            incomingCertificate.getValidityPeriod().getNotValidBefore(),
            incomingCertificate.getValidityPeriod().getNotValidAfter()
        );
    }
}
