package net.ripe.rpki.server.api.commands;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;

import java.util.Objects;
import java.util.UUID;

/**
 * <p>
 * Delete a non-hosted publisher. The non-hosted publisher must be a non-hosted CA in our system.
 * Every non-hosted CA can have multiple publishers (e.g. sub-groups in a single organisation).
 * </p>
 * <p>
 */
@Getter
@EqualsAndHashCode
public class DeleteNonHostedPublisherCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

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
