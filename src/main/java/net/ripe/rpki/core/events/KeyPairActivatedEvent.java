package net.ripe.rpki.core.events;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.server.api.commands.CommandContext;

@EqualsAndHashCode(callSuper = true)
public class KeyPairActivatedEvent extends CertificateAuthorityEvent {

    @Getter
    private final KeyPairEntity keyPair;

    public KeyPairActivatedEvent(VersionedId certificateAuthorityId, KeyPairEntity keyPair) {
        super(certificateAuthorityId);
        this.keyPair = keyPair;
    }

    @Override
    public void accept(CertificateAuthorityEventVisitor visitor, CommandContext recording) {
        visitor.visitKeyPairActivatedEvent(this, recording);
    }

    @Override
    public String toString() {
        // This string representation is stored in the command audit table and shown to the user
        IncomingResourceCertificate incomingCertificate = keyPair.getCurrentIncomingCertificate();
        return String.format(
            "Activated resource certificate [public key hash=%s, resources=%s, serial number=%s, not valid before=%s, not valid after=%s]",
            KeyPairUtil.getAsciiHexEncodedPublicKeyHash(keyPair.getPublicKey()),
            incomingCertificate.getCertifiedResources(),
            incomingCertificate.getCertificate().getSerialNumber(),
            incomingCertificate.getNotValidBefore(),
            incomingCertificate.getNotValidAfter()
        );
    }
}
