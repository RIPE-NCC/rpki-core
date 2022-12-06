package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.services.activation.CertificateAuthorityCreateService;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException;

import javax.security.auth.x500.X500Principal;

/**
 * <p>
 * Let the back-end create a Hosted Certificate Authority.
 * </p>
 * <p>
 * Note:
 * <li> You probably want to use the {@link CertificateAuthorityCreateService} instead of issuing this command directly.
 * <li> If a CertificateAuthority with the same name already exists, a {@link CertificateAuthorityNameNotUniqueException} will be thrown.
 * </p>
 */
public class ActivateHostedCertificateAuthorityCommand extends CertificateAuthorityActivationCommand {

    public ActivateHostedCertificateAuthorityCommand(VersionedId certificateAuthorityId, X500Principal name, ImmutableResourceSet resources, long parentId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER, name, resources, parentId);
    }

    @Override
    public String getCommandSummary() {
        return "Created and activated Certificate Authority '" + name + "' with resources " + resources;
    }
}
