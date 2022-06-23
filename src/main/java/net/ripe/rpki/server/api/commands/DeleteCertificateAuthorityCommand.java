package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;

import javax.security.auth.x500.X500Principal;

/**
 * <p>
 * Delete the mentioned Certificate Authority. Use with extreme prejudice...
 * </p>
 */
public class DeleteCertificateAuthorityCommand extends ChildParentCertificateAuthorityCommand {
    private final X500Principal name;
    private final RoaConfigurationData roaConfiguration;

    private static final long serialVersionUID = 2L;

    public DeleteCertificateAuthorityCommand(VersionedId certificateAuthorityId, X500Principal name, RoaConfigurationData roaConfiguration) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.name = name;
        this.roaConfiguration = roaConfiguration;
    }

    @Override
    public String getCommandSummary() {
        return "Deleted Certificate Authority '" + name + "' with ROAs: " + roaConfiguration.getPrefixes() + ".";
    }
}
