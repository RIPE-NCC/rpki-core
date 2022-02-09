package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;

public interface CertificateAuthorityCommandHandler <T extends CertificateAuthorityCommand> {

	Class<T> commandType();
	void handle(T command, CommandStatus commandStatus);

}
