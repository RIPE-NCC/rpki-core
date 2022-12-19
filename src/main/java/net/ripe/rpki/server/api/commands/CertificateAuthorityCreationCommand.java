package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

/**
 * Base class for commands that create a certificate authority.
 */
@Getter
public abstract class CertificateAuthorityCreationCommand extends CertificateAuthorityCommand {

    @NonNull private final X500Principal name;
    @NonNull private final UUID uuid;
    @NonNull private final ImmutableResourceSet resources;

    protected CertificateAuthorityCreationCommand(VersionedId certificateAuthorityId, X500Principal name, UUID uuid, ImmutableResourceSet resources) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.name = name;
        this.uuid = uuid;
        this.resources = resources;
    }
}
