package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertIgnoredAnnouncement;
import net.ripe.rpki.server.api.commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static net.ripe.rpki.domain.ProductionCertificateAuthorityTest.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class UpdateRoaAlertIgnoredAnnouncedRoutesCommandHandlerTest {

    private static final Long TEST_CA_ID = 2L;
    private static final VersionedId TEST_VERSIONED_CA_ID = new VersionedId(TEST_CA_ID);

    private static final AnnouncedRoute ANNOUNCEMENT_1 = new AnnouncedRoute(Asn.parse("AS3333"), IpRange.parse("127.0.0.0/8"));

    private UpdateRoaAlertIgnoredAnnouncedRoutesCommandHandler subject;
    private HostedCertificateAuthority certificateAuthority;
    private CertificateAuthorityRepository certificateAuthorityRepository;
    private RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @Before
    public void setUp() {
        certificateAuthority = createInitialisedProdCaWithRipeResources(TestServices.createCertificateManagementService());
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        roaAlertConfigurationRepository = mock(RoaAlertConfigurationRepository.class);

        subject = new UpdateRoaAlertIgnoredAnnouncedRoutesCommandHandler(certificateAuthorityRepository, roaAlertConfigurationRepository);
    }

    @Test
    public void should_create_new_roa_alert_configuration_for_ignored_announced_routes() {
        UpdateRoaAlertIgnoredAnnouncedRoutesCommand command = new UpdateRoaAlertIgnoredAnnouncedRoutesCommand(
                TEST_VERSIONED_CA_ID,
                Collections.singleton(ANNOUNCEMENT_1),
                Collections.emptySet());
        when(certificateAuthorityRepository.findHostedCa(TEST_CA_ID)).thenReturn(certificateAuthority);

        subject.handle(command);

        ArgumentCaptor<RoaAlertConfiguration> configuration = ArgumentCaptor.forClass(RoaAlertConfiguration.class);
        verify(roaAlertConfigurationRepository).add(configuration.capture());

        assertEquals("ignored announcement added", Collections.singleton(new RoaAlertIgnoredAnnouncement(ANNOUNCEMENT_1)), configuration.getValue().getIgnored());
    }

    @Test
    public void should_update_existing_roa_alert_configuration_for_ignored_announced_routes() {
        RoaAlertConfiguration configuration = new RoaAlertConfiguration(certificateAuthority);
        configuration.update(Collections.singleton(ANNOUNCEMENT_1), Collections.emptySet());
        UpdateRoaAlertIgnoredAnnouncedRoutesCommand command = new UpdateRoaAlertIgnoredAnnouncedRoutesCommand(
                TEST_VERSIONED_CA_ID,
                Collections.emptySet(),
                Collections.singleton(ANNOUNCEMENT_1));
        when(certificateAuthorityRepository.findHostedCa(TEST_CA_ID)).thenReturn(certificateAuthority);
        when(roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(TEST_CA_ID)).thenReturn(configuration);

        subject.handle(command);

        assertEquals("ignored announcement removed", Collections.<AnnouncedRoute>emptySet(), configuration.getIgnored());
    }
}
