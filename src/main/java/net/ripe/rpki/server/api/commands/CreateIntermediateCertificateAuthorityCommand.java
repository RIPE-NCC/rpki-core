package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

/**
 * <p>
 * Create an intermediate CA
 * </p>
 */
public class CreateIntermediateCertificateAuthorityCommand extends CertificateAuthorityActivationCommand {

    public CreateIntermediateCertificateAuthorityCommand(VersionedId certificateAuthorityId, X500Principal name, long parentId) {
        super(certificateAuthorityId, name, UUID.randomUUID(), ImmutableResourceSet.empty(), parentId);
    }

    @Override
    public String getCommandSummary() {
        return String.format("Created Intermediate Certificate Authority '%s'.", getName());
    }
}
