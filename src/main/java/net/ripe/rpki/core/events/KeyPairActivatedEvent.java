package net.ripe.rpki.core.events;

import net.ripe.rpki.commons.util.VersionedId;

public class KeyPairActivatedEvent extends CertificateAuthorityEvent {

    private static final long serialVersionUID = 1L;
    private final String keyPairName;


    public KeyPairActivatedEvent(VersionedId certificateAuthorityId, String keyPairName) {
        super(certificateAuthorityId);
        this.keyPairName = keyPairName;
    }

    public String getKeyPairName() {
        return keyPairName;
    }

    @Override
    public void accept(CertificateAuthorityEventVisitor visitor) {
        visitor.visitKeyPairActivatedEvent(this);
    }
}
