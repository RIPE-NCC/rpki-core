package net.ripe.rpki.services.impl.background;

import jakarta.transaction.Transactional;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.server.api.ports.InternalNamePresenter;
import net.ripe.rpki.server.api.security.CertificationUserId;
import net.ripe.rpki.services.impl.email.EmailSender;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.security.auth.x500.X500Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@Transactional
public class RoaNotificationServiceTest extends CertificationDomainTestCase {

    private static final long HOSTED_CA_ID = 454L;
    private static final X500Principal CHILD_CA_NAME = new X500Principal("CN=child");

    private static final String ADMIN_RIPE_NET = "admin@ripe.net";
    private static final CertificationUserId USER_ID = new CertificationUserId(UUID.randomUUID());

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @Autowired
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @MockBean
    private InternalNamePresenter internalNamePresenter;

    private RoaNotificationService roaNotificationService;

    private HostedCertificateAuthority childCa;

    @Before
    @Override
    public void setupTest() {
        clearDatabase();
        var parent = createInitialisedProdCaWithRipeResources();
        certificateAuthorityRepository.add(parent);
        childCa = new HostedCertificateAuthority(HOSTED_CA_ID, CHILD_CA_NAME, UUID.randomUUID(), parent);
        certificateAuthorityRepository.add(childCa);
        when(internalNamePresenter.humanizeCaName(CHILD_CA_NAME)).thenReturn("Better name");
        when(internalNamePresenter.humanizeUserPrincipal(USER_ID.getId().toString())).thenReturn(ADMIN_RIPE_NET);
        roaNotificationService = new RoaNotificationService(roaAlertConfigurationRepository, emailSender, internalNamePresenter);
    }

    @Test
    public void testNotifyNobodyBasedOnFlag() {
        RoaAlertConfiguration r = new RoaAlertConfiguration(childCa, "bad@ripe.net", List.of(RouteValidityState.UNKNOWN), RoaAlertFrequency.WEEKLY);
        r.setNotifyOnRoaChanges(false);
        roaAlertConfigurationRepository.add(r);
        var messages = roaNotificationService.notifyAboutRoaChanges(childCa, USER_ID, Collections.emptyList(), Collections.emptyList());
        assertEquals(0, messages.size());
    }

    @Test
    public void testNoNotifyIfNothingChanges() {
        var email = "bad@ripe.net";
        RoaAlertConfiguration r = new RoaAlertConfiguration(childCa, email, List.of(RouteValidityState.UNKNOWN), RoaAlertFrequency.WEEKLY);
        r.setNotifyOnRoaChanges(true);
        roaAlertConfigurationRepository.add(r);
        var messages = roaNotificationService.notifyAboutRoaChanges(childCa, USER_ID, Collections.emptyList(), Collections.emptyList());
        assertEquals(0, messages.size());
    }

    @Test
    public void testNotifyRoas() {
        var email = "bad@ripe.net";
        RoaAlertConfiguration r = new RoaAlertConfiguration(childCa, email, List.of(RouteValidityState.UNKNOWN), RoaAlertFrequency.WEEKLY);
        r.setNotifyOnRoaChanges(true);
        roaAlertConfigurationRepository.add(r);

        var now = Instant.now();
        var roa1 = new RoaConfigurationPrefix(Asn.parse("AS64396"), IpRange.parse("192.0.2.0/24"), null, now);
        var roa2 = new RoaConfigurationPrefix(Asn.parse("AS64397"), IpRange.parse("198.51.100.0/24"), 32, now);
        var roa3 = new RoaConfigurationPrefix(Asn.parse("AS123"), IpRange.parse("fd00:550:ffff:ffff:ffff:ffff:ffff:ffff/128"), 128, now);

        var messages = roaNotificationService.notifyAboutRoaChanges(childCa, USER_ID, List.of(roa1, roa3), List.of(roa2));
        assertEquals(1, messages.size());

        var message = messages.iterator().next();
        assertTrue(message.body().contains("123       fd00:550:ffff:ffff:ffff:ffff:ffff:ffff/128        128         A"));
        assertTrue(message.body().contains("64396     192.0.2.0/24                                      24          A"));
        assertTrue(message.body().contains("64397     198.51.100.0/24                                   32          D"));
        assertTrue(message.body().contains(
                "This is an automated email to inform you that user admin@ripe.net made changes\n" +
                        "to one or more ROAs for your organisation Better name."));
    }

}