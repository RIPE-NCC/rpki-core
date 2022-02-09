package net.ripe.rpki.core.events;

import net.ripe.rpki.commons.util.VersionedId;


public class IncomingCertificateActivatedEvent extends CertificateAuthorityEvent {

    private static final long serialVersionUID = 1L;

    private final String keyPairName;


    public IncomingCertificateActivatedEvent(VersionedId certificateAuthorityId, String keyPairName) {
        super(certificateAuthorityId);
        this.keyPairName = keyPairName;
    }

    @Override
    public void accept(CertificateAuthorityEventVisitor visitor) {
        visitor.visitIncomingCertificateActivatedEvent(this);
    }

    public String getKeyPairName() {
        return keyPairName;
    }
}
