package net.ripe.rpki.core.events;


public interface CertificateAuthorityEventVisitor {

    void visitKeyPairActivatedEvent(KeyPairActivatedEvent event);
}
