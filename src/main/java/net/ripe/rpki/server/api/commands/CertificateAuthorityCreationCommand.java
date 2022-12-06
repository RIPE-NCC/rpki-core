package net.ripe.rpki.server.api.commands;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.Validate;

/**
 * Base class for commands that create a certificate authority.
 */
public abstract class CertificateAuthorityCreationCommand extends CertificateAuthorityCommand {

    private final ImmutableResourceSet resources;

    public CertificateAuthorityCreationCommand(VersionedId certificateAuthorityId, ImmutableResourceSet resources) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        Validate.notNull(resources, "resources are required");
        this.resources = resources;
    }

    public ImmutableResourceSet getResources() {
    	return resources;
    }
}
