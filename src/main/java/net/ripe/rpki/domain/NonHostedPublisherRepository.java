package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@SequenceGenerator(name = "seq", sequenceName = "seq_all", allocationSize = 1)
@Table(name = "non_hosted_publisher_repository")
public class NonHostedPublisherRepository extends EntitySupport  {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq")
    @Getter
    private Long id;

    @Getter
    @Basic(optional = false)
    @Column(name = "publisher_handle", nullable = false)
    private UUID publisherHandle;

    @Getter
    @Basic(optional = false)
    @Column(name = "publisher_request", nullable = false)
    private PublisherRequest publisherRequest;

    @Getter
    @Basic(optional = false)
    @Column(name = "repository_response", nullable = false)
    private RepositoryResponse repositoryResponse;

    public NonHostedPublisherRepository() {}

    public NonHostedPublisherRepository(UUID publisherHandle, PublisherRequest publisherRequest, RepositoryResponse repositoryResponse) {
        this.publisherHandle = publisherHandle;
        this.publisherRequest = publisherRequest;
        this.repositoryResponse = repositoryResponse;
    }

}
