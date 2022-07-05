package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationServiceBean;
import net.ripe.rpki.server.api.commands.GenerateOfflineCARepublishRequestCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;


public class GenerateOfflineCARepublishRequestCommandHandlerTest {

    private static final VersionedId CA_VERSIONED_ID = new VersionedId(0l);

    private AllResourcesCertificateAuthority allResourcesCa;
    private CertificateAuthorityRepository certificateAuthorityRepository;
    private RepositoryConfiguration certificationConfiguration;

    private GenerateOfflineCARepublishRequestCommandHandler subject;

    @Before
    public void setUp() {
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        certificationConfiguration = mock(RepositoryConfiguration.class);

        CertificateRequestCreationServiceBean requestCreationService =
                new CertificateRequestCreationServiceBean(certificationConfiguration);
        subject = new GenerateOfflineCARepublishRequestCommandHandler(certificateAuthorityRepository, requestCreationService);
    }

    @Test
    public void shouldReturnCorrectType() {
        assertEquals(GenerateOfflineCARepublishRequestCommand.class, subject.commandType());
    }

    @Test
    public void shouldAddPendingRepublishRequestToAllResourcesCA() throws Exception {
        ArgumentCaptor<UpStreamCARequestEntity> capturedArgument = ArgumentCaptor.forClass(UpStreamCARequestEntity.class);
        allResourcesCa = mock(AllResourcesCertificateAuthority.class);

        when(certificateAuthorityRepository.findHostedCa(CA_VERSIONED_ID.getId())).thenReturn(allResourcesCa);
        URI notificationUri = new URI("http://bla.com/notification.xml");
        when(certificationConfiguration.getNotificationUri()).thenReturn(notificationUri);
        when(certificationConfiguration.getTrustAnchorRepositoryUri()).thenReturn(notificationUri);
        when(allResourcesCa.isAllResourcesCa()).thenReturn(true);

        subject.handle(new GenerateOfflineCARepublishRequestCommand(CA_VERSIONED_ID));

        verify(allResourcesCa).setUpStreamCARequestEntity(capturedArgument.capture());
        assertEquals(0, capturedArgument.getValue().getUpStreamCARequest().getTaRequests().size());
        X509CertificateInformationAccessDescriptor[] siaDescriptors = capturedArgument.getValue().getUpStreamCARequest().getSiaDescriptors();
        assertEquals(1, siaDescriptors.length);
        assertEquals(notificationUri, siaDescriptors[0].getLocation());
    }
}
