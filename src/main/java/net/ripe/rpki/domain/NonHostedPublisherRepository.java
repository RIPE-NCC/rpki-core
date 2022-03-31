package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.ncc.core.domain.support.EntitySupport;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
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
