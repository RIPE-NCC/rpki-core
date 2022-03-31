package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProvisionNonHostedPublisherCommandHandlerTest {

    public static final UUID PUBLISHER_HANDLE = UUID.randomUUID();

    private NonHostedCertificateAuthority nonHostedCertificateAuthority;
    private CertificateAuthorityRepository certificateAuthorityRepository;
    private NonHostedPublisherRepositoryService nonHostedPublisherRepositoryService;
    private ProvisionNonHostedPublisherCommandHandler subject;

    @Before
    public void setUp() {
        nonHostedCertificateAuthority = new NonHostedCertificateAuthority(123L, new X500Principal("CN=non-hosted"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, null);
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        nonHostedPublisherRepositoryService = mock(NonHostedPublisherRepositoryService.class);
        subject = new ProvisionNonHostedPublisherCommandHandler(certificateAuthorityRepository, nonHostedPublisherRepositoryService);
    }

    @Test
    public void should_track_created_repository() {
        PublisherRequest publisherRequest = new PublisherRequest(ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT);
        when(certificateAuthorityRepository.findNonHostedCa(123L)).thenReturn(nonHostedCertificateAuthority);
        RepositoryResponse repositoryResponse = new RepositoryResponse(
            Optional.empty(),
            URI.create("https://rpki.example.com/"),
            PUBLISHER_HANDLE.toString(),
            URI.create("rsync://rpki.example.com/repo/handle/"),
            Optional.empty(),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT_2
        );
        when(nonHostedPublisherRepositoryService.provisionPublisher(PUBLISHER_HANDLE, publisherRequest))
            .thenReturn(repositoryResponse);

        subject.handle(new ProvisionNonHostedPublisherCommand(new VersionedId(123L, 1), PUBLISHER_HANDLE, publisherRequest));

        verify(nonHostedPublisherRepositoryService).provisionPublisher(PUBLISHER_HANDLE, publisherRequest);
        assertThat(nonHostedCertificateAuthority.getPublisherRepositories()).hasSize(1).allSatisfy(repository -> {
            assertThat(repository.getPublisherHandle()).isEqualTo(PUBLISHER_HANDLE);
            assertThat(repository.getPublisherRequest()).isEqualTo(publisherRequest);
            assertThat(repository.getRepositoryResponse()).isEqualTo(repositoryResponse);
        });
    }
}
