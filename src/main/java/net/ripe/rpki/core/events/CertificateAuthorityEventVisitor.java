package net.ripe.rpki.core.events;


import net.ripe.rpki.server.api.commands.CommandContext;

public interface CertificateAuthorityEventVisitor {
    default void visitKeyPairActivatedEvent(KeyPairActivatedEvent event, CommandContext context) {}

    default void visitIncomingCertificateUpdatedEvent(IncomingCertificateUpdatedEvent event, CommandContext context) {}
}
