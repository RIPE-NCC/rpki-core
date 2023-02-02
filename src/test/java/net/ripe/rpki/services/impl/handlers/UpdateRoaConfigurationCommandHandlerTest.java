package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.roa.RoaConfiguration;
import net.ripe.rpki.domain.roa.RoaConfigurationPrefix;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.command.EntityTagDoesNotMatchException;
import net.ripe.rpki.server.api.services.command.NotHolderOfResourcesException;
import net.ripe.rpki.server.api.services.command.PrivateAsnsUsedException;
import net.ripe.rpki.services.impl.background.RoaMetricsService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class UpdateRoaConfigurationCommandHandlerTest {

    private static final Asn ASN = Asn.parse("1234");
    private static final String PRIVATE_ASNS = "64512-65535, 4200000000-4294967294";

    private static final Asn PRIVATE_ASN = Asn.parse("AS64614");

    private static final IpRange PREFIX = IpRange.parse("10.1/16");

    private ManagedCertificateAuthority certificateAuthority;

    private CertificateAuthorityRepository certificateAuthorityRepository;

    private RoaConfigurationRepository roaConfigurationRepository;

    private UpdateRoaConfigurationCommandHandler subject;

    private RoaConfiguration configuration;

    private RoaMetricsService roaMetricsService;

    @Before
    public void setUp() {
        certificateAuthority = TestObjects.createInitialisedProdCaWithRipeResources();
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        roaConfigurationRepository = mock(RoaConfigurationRepository.class);
        roaMetricsService = mock(RoaMetricsService.class);

        when(certificateAuthorityRepository.findManagedCa(certificateAuthority.getId())).thenReturn(certificateAuthority);

        subject = new UpdateRoaConfigurationCommandHandler(certificateAuthorityRepository, roaConfigurationRepository
                , PRIVATE_ASNS, roaMetricsService);
        configuration = new RoaConfiguration(certificateAuthority);

        when(roaConfigurationRepository.getOrCreateByCertificateAuthority(certificateAuthority)).thenReturn(configuration);
    }

    @Test
    public void should_add_new_additions() {
        subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.of(configuration.convertToData().entityTag()),
            Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX, null)),
            Collections.emptyList()));

        assertThat(configuration.getPrefixes()).isEqualTo(Collections.singleton(new RoaConfigurationPrefix(ASN, PREFIX, null)));
        verify(roaMetricsService).countAdded(1);
    }

    @Test
    public void should_reject_if_etag_does_not_match_current_configuration() {
        assertThatThrownBy(() -> subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.of("bad-etag"),
            Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX, null)),
            Collections.emptyList()
        ))).isInstanceOf(EntityTagDoesNotMatchException.class);
    }

    @Test(expected = PrivateAsnsUsedException.class)
    public void should_reject_new_additions_of_private_ASN() {
        subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
        Optional.empty(),
            Collections.singletonList(new RoaConfigurationPrefixData(PRIVATE_ASN, PREFIX, null)),
            Collections.emptyList()));
        verifyNoMoreInteractions(roaMetricsService);
    }

    @Test
    public void should_reject_uncertified_prefixes() {
        UpdateRoaConfigurationCommand command = new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.empty(),
            Collections.singletonList(new RoaConfigurationPrefixData(ASN, IpRange.parse("1.0.0.0/8"), null)),
            Collections.emptyList()
        );
        assertThatThrownBy(() -> subject.handle(command)).isInstanceOf(NotHolderOfResourcesException.class);
    }

    @Test
    public void should_remove_deletions() {
        configuration.addPrefix(Collections.singleton(new RoaConfigurationPrefix(ASN, PREFIX, null)));

        subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.empty(),
            Collections.emptyList(),
            Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX, null))));

        assertThat(configuration.getPrefixes()).isEmpty();
        verify(roaConfigurationRepository).logRoaPrefixDeletion(configuration, Collections.singleton(new RoaConfigurationPrefix(ASN, PREFIX, null)));
        verify(roaMetricsService).countDeleted(1);
    }

    @Test
    public void should_notify_roa_entity_service_on_configuration_change() {
        certificateAuthority.markConfigurationApplied();
        assertThat(certificateAuthority.isConfigurationCheckNeeded()).isFalse();

        subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.empty(),
            Collections.emptyList(),
            Collections.emptyList()));

        assertThat(certificateAuthority.isConfigurationCheckNeeded()).isTrue();
    }
}
