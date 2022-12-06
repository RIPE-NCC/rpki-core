package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.Validate;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

/**
 * <p>
 * Let the back-end create a non-Hosted Certificate Authority.
 * </p>
 * <p>
 * Note:
 * <li> You probably want to use the {@link net.ripe.rpki.server.api.services.activation.CertificateAuthorityCreateService} instead of issuing this command directly.
 * <li> If a CertificateAuthority with the same name already exists, a {@link net.ripe.rpki.server.api.services.command.CertificateAuthorityNameNotUniqueException} will be thrown.
 * </p>
 */
@Getter
public class ActivateNonHostedCertificateAuthorityCommand extends CertificateAuthorityActivationCommand {

    private final ProvisioningIdentityCertificate identityCertificate;
    private final UUID uuid;

    public ActivateNonHostedCertificateAuthorityCommand(VersionedId certificateAuthorityId,
                                                        X500Principal name,
                                                        UUID uuid,
                                                        ImmutableResourceSet resources,
                                                        ProvisioningIdentityCertificate identityCertificate,
                                                        long parentId) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER, name, resources, parentId);
        Validate.notNull(identityCertificate, "identityCertificate is required");
        this.identityCertificate = identityCertificate;
        this.uuid = uuid;
    }

    @Override
    public String getCommandSummary() {
        return String.format("Created non-Hosted Certificate Authority '%s' with uuid '%s' and with resources '%s'",
                name, uuid, resources.toString());
    }
}
