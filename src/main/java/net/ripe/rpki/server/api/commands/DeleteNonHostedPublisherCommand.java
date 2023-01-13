package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;

import java.util.Objects;
import java.util.UUID;

/**
 * <p>
 * Delete the non-hosted publisher repository information from a CA. This must be a non-hosted CA in our system.
 * The actual removal of the repository from Krill must be done separately, after this command succeeds.
 * </p>
 */
@Getter
public class DeleteNonHostedPublisherCommand extends CertificateAuthorityModificationCommand {

    private final UUID publisherHandle;

    public DeleteNonHostedPublisherCommand(VersionedId certificateAuthorityId, UUID publisherHandle) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.publisherHandle = Objects.requireNonNull(publisherHandle);
    }

    @Override
    public String getCommandSummary() {
        return "Delete repository for non-hosted publisher with handle: " + publisherHandle;
    }
}