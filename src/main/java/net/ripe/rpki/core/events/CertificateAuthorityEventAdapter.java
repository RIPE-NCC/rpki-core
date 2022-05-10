package net.ripe.rpki.core.events;


public abstract class CertificateAuthorityEventAdapter implements CertificateAuthorityEventVisitor {

    @Override
    public void visitKeyPairActivatedEvent(KeyPairActivatedEvent event) {
    }
}
