package net.ripe.rpki.core.events;


public interface CertificateAuthorityEventVisitor {

    void visitIncomingCertificateActivatedEvent(IncomingCertificateActivatedEvent event);

    void visitKeyPairActivatedEvent(KeyPairActivatedEvent event);
}
