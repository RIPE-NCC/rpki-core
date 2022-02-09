package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

/**
 * <p>
 * Update the resources assigned to the specified CA. If the resources change the CA will request a new resource certificate.
 * </p><p>
 * NOTE: This command is used by the back-end in a background service. There should be no need to use this directly.
 * </p>
 * Currently only used for the production CA and deserializing old command history.
 */
public class ProductionCaResourcesCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    @Getter
    private final IpResourceSet resourceClasses;

    public ProductionCaResourcesCommand(VersionedId certificateAuthorityId, IpResourceSet resourceClasses) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        Validate.notNull(resourceClasses, "resourceClasses are required");
        this.resourceClasses = resourceClasses;
    }

    @Override
    public String getCommandSummary() {
        return "Updated Certificate Authority resources to " + StringUtils.join(resourceClasses.iterator(), ", ");
    }
}
