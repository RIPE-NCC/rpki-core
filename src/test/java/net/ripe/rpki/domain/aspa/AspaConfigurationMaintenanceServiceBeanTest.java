package net.ripe.rpki.domain.aspa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.events.IncomingCertificateUpdatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfigurationMaintenanceServiceBean.AspaConfigurationUpdatedDueToChangedResourcesEvent;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.TreeMap;

import static net.ripe.rpki.domain.TestObjects.CA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AspaConfigurationMaintenanceServiceBeanTest  {

    public static final VersionedId CERTIFICATE_AUTHORITY_ID = new VersionedId(CA_ID);
    public static final Asn CUSTOMER_ASN = Asn.parse("AS1");
    public static final Asn PROVIDER_ASN = Asn.parse("AS2");

    @Mock
    private AspaConfigurationRepository aspaConfigurationRepository;
    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private ManagedCertificateAuthority certificateAuthority;
    @InjectMocks
    private AspaConfigurationMaintenanceServiceBean subject;
    private AspaConfiguration aspaConfiguration;
    private final CommandContext commandContext = new CommandContext(new UpdateAllIncomingResourceCertificatesCommand(CERTIFICATE_AUTHORITY_ID, Integer.MAX_VALUE));

    @Before
    public void setUp() {
        aspaConfiguration = new AspaConfiguration(certificateAuthority, CUSTOMER_ASN, Collections.singletonMap(PROVIDER_ASN, AspaAfiLimit.IPv4));
        when(certificateAuthority.getVersionedId()).thenReturn(CERTIFICATE_AUTHORITY_ID);
        when(certificateAuthorityRepository.findManagedCa(CA_ID)).thenReturn(certificateAuthority);
        when(aspaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(new TreeMap<>(Collections.singletonMap(CUSTOMER_ASN, aspaConfiguration)));
    }

    @Test
    public void should_not_remove_customer_asns_included_in_certified_resources() {
        subject.visitIncomingCertificateUpdatedEvent(new IncomingCertificateUpdatedEvent(CERTIFICATE_AUTHORITY_ID, X509ResourceCertificateTest.createSelfSignedCaResourceCertificate(new IpResourceSet(CUSTOMER_ASN))), commandContext);

        verify(aspaConfigurationRepository, never()).remove(aspaConfiguration);
        assertThat(commandContext.getRecordedEvents()).isEmpty();
    }

    @Test
    public void should_remove_customer_asns_not_included_in_certified_resources() {
        subject.visitIncomingCertificateUpdatedEvent(new IncomingCertificateUpdatedEvent(CERTIFICATE_AUTHORITY_ID, X509ResourceCertificateTest.createSelfSignedCaResourceCertificate(IpResourceSet.ALL_PRIVATE_USE_RESOURCES)), commandContext);

        verify(aspaConfigurationRepository).remove(aspaConfiguration);
        assertThat(commandContext.getRecordedEvents()).hasSize(1).allSatisfy(recordedEvent -> {
            assertThat(recordedEvent).isInstanceOfSatisfying(AspaConfigurationUpdatedDueToChangedResourcesEvent.class, updatedEvent -> {
                assertThat(updatedEvent.getCaId()).isEqualTo(CERTIFICATE_AUTHORITY_ID);
                assertThat(updatedEvent.getRemoved()).isEqualTo(Collections.singletonList(aspaConfiguration.toData()));
            });
        });
    }
}
