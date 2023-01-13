package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import lombok.NonNull;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.util.VersionedId;

import java.util.Objects;
import java.util.UUID;

/**
 * <p>
 * Add the repository information of a provisioned Krill repository to the non-hosted CA.
 * Every non-hosted CA can have multiple publishers (e.g. sub-groups in a single organisation).
 * </p>
 */
@Getter
public class ProvisionNonHostedPublisherCommand extends CertificateAuthorityModificationCommand {

    private final UUID publisherHandle;
    private final PublisherRequest publisherRequest;
    private final RepositoryResponse repositoryResponse;

    public ProvisionNonHostedPublisherCommand(@NonNull VersionedId certificateAuthorityId, @NonNull UUID publisherHandle, @NonNull PublisherRequest publisherRequest, @NonNull RepositoryResponse repositoryResponse) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.publisherHandle = publisherHandle;
        this.publisherRequest = publisherRequest;
        this.repositoryResponse = repositoryResponse;
    }

    @Override
    public String getCommandSummary() {
        return "Provision repository for non-hosted publisher with handle: " + publisherHandle;
    }
}
