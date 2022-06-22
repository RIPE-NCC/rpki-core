package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.util.VersionedId;

import java.util.Objects;
import java.util.UUID;

/**
 * <p>
 * Create a repository for a non-hosted publisher. The non-hosted publisher must be a non-hosted CA in our system.
 * Every non-hosted CA can have multiple publishers (e.g. sub-groups in a single organisation).
 * </p>
 * <p>
 * The provided publisher handle is used instead of the publisher handle in the request, since we want to
 * determine the handle to use, not the non-hosted CA.
 * </p>
 */
@Getter
public class ProvisionNonHostedPublisherCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    private final UUID publisherHandle;
    private final PublisherRequest publisherRequest;

    public ProvisionNonHostedPublisherCommand(VersionedId certificateAuthorityId, UUID publisherHandle, PublisherRequest publisherRequest) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.publisherHandle = Objects.requireNonNull(publisherHandle);
        this.publisherRequest = Objects.requireNonNull(publisherRequest);
    }

    @Override
    public String getCommandSummary() {
        return "Provision repository for non-hosted publisher with handle: " + publisherHandle;
    }
}
