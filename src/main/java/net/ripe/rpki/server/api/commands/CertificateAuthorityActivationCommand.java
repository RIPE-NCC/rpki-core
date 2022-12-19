package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

@Getter
public abstract class CertificateAuthorityActivationCommand extends CertificateAuthorityCreationCommand {
    protected final long parentId;

    protected CertificateAuthorityActivationCommand(
        VersionedId certificateAuthorityId,
        X500Principal name,
        UUID uuid,
        ImmutableResourceSet resources,
        long parentId
    ) {
        super(certificateAuthorityId, name, uuid, resources);
        this.parentId = parentId;
    }
}
