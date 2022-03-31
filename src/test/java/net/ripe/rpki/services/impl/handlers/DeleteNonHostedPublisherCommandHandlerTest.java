package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.DeleteNonHostedPublisherCommand;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
public class DeleteNonHostedPublisherCommandHandlerTest extends CertificationDomainTestCase {

    public static final UUID PUBLISHER_HANDLE = UUID.randomUUID();

    private DeleteNonHostedPublisherCommandHandler subject;

    private ProductionCertificateAuthority prodCa;
    private NonHostedCertificateAuthority nonHostedCertificateAuthority;

    @Inject
    private NonHostedPublisherRepositoryService nonHostedPublisherRepositoryService;

    private PublisherRequest publisherRequest =
            new PublisherRequest(ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT);

    private RepositoryResponse repositoryResponse = new RepositoryResponse(
            publisherRequest.getTag(),
            URI.create("https://fake.rpki.example.com/pubserver/").resolve(PUBLISHER_HANDLE.toString()),
            PUBLISHER_HANDLE.toString(),
            URI.create("rsync://fake.rsync.example.com/repository/").resolve(PUBLISHER_HANDLE.toString()),
            Optional.of(URI.create("rrdp://fake.rrdp.example.com/notification.xml")),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT_2
    );


    @Before
    public void setUp() {
        clearDatabase();
        prodCa = createInitialisedProdCaWithRipeResources();
        nonHostedCertificateAuthority = new NonHostedCertificateAuthority(123L, new X500Principal("CN=non-hosted"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, prodCa);
        nonHostedCertificateAuthority.addNonHostedPublisherRepository(PUBLISHER_HANDLE, publisherRequest, repositoryResponse);
        certificateAuthorityRepository.add(nonHostedCertificateAuthority);

        subject = new DeleteNonHostedPublisherCommandHandler(certificateAuthorityRepository, nonHostedPublisherRepositoryService);
    }

    @Test
    public void should_handle_deletion_of_publisher() {
        //Before
        assertThat(nonHostedCertificateAuthority.getPublisherRepositories()).hasSize(1);

        subject.handle(new DeleteNonHostedPublisherCommand(new VersionedId(123L, 1), PUBLISHER_HANDLE));
        entityManager.flush();

        //After
        assertThat(nonHostedCertificateAuthority.getPublisherRepositories()).isEmpty();

        assertThatThrownBy(
            () -> subject.handle(new DeleteNonHostedPublisherCommand(new VersionedId(123L, 1), PUBLISHER_HANDLE))
        ).isInstanceOf(EntityNotFoundException.class);
    }
}
