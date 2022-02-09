package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.Validate;

/**
 * Base class for commands that create a certificate authority.
 */
public abstract class CertificateAuthorityCreationCommand extends CertificateAuthorityCommand {

    private static final long serialVersionUID = 1L;

    private final IpResourceSet resources;

    public CertificateAuthorityCreationCommand(VersionedId certificateAuthorityId, IpResourceSet resources) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        Validate.notNull(resources, "resources are required");
        this.resources = resources;
    }

    public IpResourceSet getResources() {
    	return resources;
    }
}
