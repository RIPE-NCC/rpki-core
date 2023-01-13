package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.server.api.commands.ProvisionNonHostedPublisherCommand;
import net.ripe.rpki.server.api.services.command.CertificationResourceLimitExceededException;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.domain.NonHostedCertificateAuthority.PUBLISHER_REPOSITORIES_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProvisionNonHostedPublisherCommandHandlerTest {

    public static final UUID PUBLISHER_HANDLE = UUID.randomUUID();

    private NonHostedCertificateAuthority nonHostedCertificateAuthority;
    private CertificateAuthorityRepository certificateAuthorityRepository;
    private ProvisionNonHostedPublisherCommandHandler subject;

    @Before
    public void setUp() {
        nonHostedCertificateAuthority = new NonHostedCertificateAuthority(123L, new X500Principal("CN=non-hosted"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, null);
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        subject = new ProvisionNonHostedPublisherCommandHandler(certificateAuthorityRepository);
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

        subject.handle(new ProvisionNonHostedPublisherCommand(new VersionedId(123L, 1), PUBLISHER_HANDLE, publisherRequest, repositoryResponse));

        assertThat(nonHostedCertificateAuthority.getPublisherRepositories()).hasSize(1).allSatisfy((handle, repository) -> {
            assertThat(handle).isEqualTo(PUBLISHER_HANDLE);
            assertThat(repository.getPublisherHandle()).isEqualTo(PUBLISHER_HANDLE);
            assertThat(repository.getPublisherRequest()).isEqualTo(publisherRequest);
            assertThat(repository.getRepositoryResponse()).isEqualTo(repositoryResponse);
        });
    }

    @Test
    public void should_limit_number_of_repositories_per_ca() {
        when(certificateAuthorityRepository.findNonHostedCa(123L)).thenReturn(nonHostedCertificateAuthority);
        PublisherRequest publisherRequest = new PublisherRequest(ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT);
        RepositoryResponse repositoryResponse = new RepositoryResponse(
            Optional.empty(),
            URI.create("https://rpki.example.com/"),
            PUBLISHER_HANDLE.toString(),
            URI.create("rsync://rpki.example.com/repo/handle/"),
            Optional.empty(),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT_2
        );

        for (int i = 0; i < PUBLISHER_REPOSITORIES_LIMIT; i++) {
            nonHostedCertificateAuthority.addNonHostedPublisherRepository(UUID.randomUUID(), publisherRequest, repositoryResponse);
        }

        assertThatThrownBy(() -> {
            subject.handle(new ProvisionNonHostedPublisherCommand(new VersionedId(123L, 1), PUBLISHER_HANDLE, publisherRequest, repositoryResponse));
        }).isInstanceOfSatisfying(CertificationResourceLimitExceededException.class, (e) -> {
            assertThat(e.getMessage()).isEqualTo("maximum number of publisher repositories limit exceeded");
        });
    }
}
