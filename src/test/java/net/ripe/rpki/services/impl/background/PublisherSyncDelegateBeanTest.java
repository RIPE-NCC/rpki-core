package net.ripe.rpki.services.impl.background;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.server.api.ports.NonHostedPublisherRepositoryService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.NonHostedPublisherRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PublisherSyncDelegateBeanTest {
    private PublisherSyncDelegate subject;

    @Mock
    private CertificateAuthorityViewService certificateAuthorityViewService;
    @Mock
    private NonHostedPublisherRepositoryService nonHostedPublisherRepositoryService;

    @Mock
    private ProvisioningIdentityCertificate identityCertificate;

    @Before
    public void setup() {
        subject = new PublisherSyncDelegateImpl( certificateAuthorityViewService, nonHostedPublisherRepositoryService, new SimpleMeterRegistry());
    }

    @Test
    public void shouldReturnWhenThereAreNoPublishers() {

        subject.runService();

        verify(nonHostedPublisherRepositoryService).listPublishers();
        verify(certificateAuthorityViewService).findAllPublisherRequestsFromNonHostedCAs();
    }

    @Test
    public void shouldReprovisionThoseOnlyOnCore() throws NonHostedPublisherRepositoryService.DuplicateRepositoryException {
        when(nonHostedPublisherRepositoryService.listPublishers()).thenReturn(Collections.emptySet());
        UUID publisherHandle = UUID.randomUUID();
        PublisherRequest publisherRequest =  new PublisherRequest(Optional.empty(), publisherHandle.toString(), identityCertificate, Optional.empty());

        NonHostedCertificateAuthority ca = mock(NonHostedCertificateAuthority.class);
        when(ca.getName()).thenReturn(new X500Principal("CN=non-hosted"));
        NonHostedPublisherRepository repository = mock(NonHostedPublisherRepository.class);
        when(repository.getPublisherHandle()).thenReturn(publisherHandle);
        when(repository.getPublisherRequest()).thenReturn(publisherRequest);
        when(repository.getCertificateAuthority()).thenReturn(ca);

        when(certificateAuthorityViewService.findAllPublisherRequestsFromNonHostedCAs()).thenReturn(List.of(repository));

        subject.runService();

        verify(nonHostedPublisherRepositoryService).listPublishers();
        verify(certificateAuthorityViewService).findAllPublisherRequestsFromNonHostedCAs();
        verify(nonHostedPublisherRepositoryService).provisionPublisher(publisherHandle, publisherRequest);
    }

    @Test
    public void shouldDeletePublisherOnlyOnKrill() {
       UUID publisherHandle = UUID.randomUUID();
        Set<UUID> krillPublishers = new HashSet<>();
        krillPublishers.add(publisherHandle);
        when(nonHostedPublisherRepositoryService.listPublishers()).thenReturn(krillPublishers);
        when(certificateAuthorityViewService.findAllPublisherRequestsFromNonHostedCAs()).thenReturn(List.of());

        subject.runService();

        verify(nonHostedPublisherRepositoryService ).listPublishers();
        verify(certificateAuthorityViewService).findAllPublisherRequestsFromNonHostedCAs();
        verify(nonHostedPublisherRepositoryService).deletePublisher(publisherHandle);
    }
}