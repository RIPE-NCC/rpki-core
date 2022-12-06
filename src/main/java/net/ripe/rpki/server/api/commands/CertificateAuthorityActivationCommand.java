package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.Validate;

import javax.security.auth.x500.X500Principal;

@Getter
public abstract class CertificateAuthorityActivationCommand extends CertificateAuthorityCommand {
    protected final X500Principal name;
    protected final ImmutableResourceSet resources;
    protected final long parentId;

    public CertificateAuthorityActivationCommand(VersionedId certificateAuthorityId, CertificateAuthorityCommandGroup commandGroup, X500Principal name, ImmutableResourceSet resources, long parentId) {
        super(certificateAuthorityId, commandGroup);
        Validate.notNull(name, "name is required");
        Validate.notNull(resources, "resources are required");
        this.name = name;
        this.resources = resources;
        this.parentId = parentId;
    }
}
