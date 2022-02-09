package net.ripe.rpki.server.api.services.command;

import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;

public class CommandWithoutEffectException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public CommandWithoutEffectException(CertificateAuthorityCommand command) {
        super(String.format("Discarded [%s] for Certificate Authority with id [%s]", command.getCommandType(), command.getCertificateAuthorityVersionedId()));
    }

    public CommandWithoutEffectException(String message) {
        super(message);
    }

    public CommandWithoutEffectException(String message, Throwable cause) {
        super(message, cause);
    }
}
