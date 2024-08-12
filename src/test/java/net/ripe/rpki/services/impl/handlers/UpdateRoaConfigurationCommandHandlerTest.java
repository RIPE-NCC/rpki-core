package net.ripe.rpki.services.impl.handlers;

import jakarta.transaction.Transactional;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Transactional
public class UpdateRoaConfigurationCommandHandlerTest extends CertificationDomainTestCase {

    private static final Asn ASN = Asn.parse("1234");
    private static final String PRIVATE_ASNS = "64512-65535, 4200000000-4294967294";

    private static final Asn PRIVATE_ASN = Asn.parse("AS64614");

    private static final IpRange PREFIX1 = IpRange.parse("10.0.0.0/8");
    private static final IpRange PREFIX2 = IpRange.parse("172.16.0.0/12");
    private static final IpRange PREFIX3 = IpRange.parse("192.168.0.0/16");

    private ManagedCertificateAuthority certificateAuthority;

    @Autowired
    private RoaConfigurationRepository roaConfigurationRepository;

    private UpdateRoaConfigurationCommandHandler subject;

    private RoaMetricsService roaMetricsService;

    @Before
    public void setUp() {
        clearDatabase();
        certificateAuthority = createInitialisedProdCaWithRipeResources();
        roaMetricsService = mock(RoaMetricsService.class);
        subject = new UpdateRoaConfigurationCommandHandler(certificateAuthorityRepository,
                roaConfigurationRepository, PRIVATE_ASNS, roaMetricsService);
    }

    @Test
    public void should_add_new_additions() {
        var configuration = new RoaConfiguration(certificateAuthority);
        subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.of(configuration.convertToData().entityTag()),
            Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, null)),
            Collections.emptyList()));

        var config = roaConfigurationRepository.getOrCreateByCertificateAuthority(certificateAuthority);
        assertThat(config.getPrefixes()).hasSize(1);
        RoaConfigurationPrefix p = config.getPrefixes().iterator().next();
        assertThat(p.getAsn()).isEqualTo(ASN);
        assertThat(p.getPrefix()).isEqualTo(PREFIX1);
        assertThat(p.getMaximumLength()).isEqualTo(8);
        assertThat(p.getUpdatedAt()).isNotNull();

        verify(roaMetricsService).countAdded(1);
    }

    @Test
    public void should_reject_if_etag_does_not_match_current_configuration() {
        var command = new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.of("bad-etag"),
            Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, null)),
            Collections.emptyList()
        );
        assertThatThrownBy(() -> subject.handle(command)).isInstanceOf(EntityTagDoesNotMatchException.class);
    }

    @Test(expected = PrivateAsnsUsedException.class)
    public void should_reject_new_additions_of_private_ASN() {
        subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
        Optional.empty(),
            Collections.singletonList(new RoaConfigurationPrefixData(PRIVATE_ASN, PREFIX1, null)),
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
        var configuration = new RoaConfiguration(certificateAuthority);
        configuration.addPrefixes(Collections.singleton(new RoaConfigurationPrefix(ASN, PREFIX1, null)));

        subject.handle(new UpdateRoaConfigurationCommand(
            certificateAuthority.getVersionedId(),
            Optional.empty(),
            Collections.emptyList(),
            Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, null))));

        var config = roaConfigurationRepository.getOrCreateByCertificateAuthority(certificateAuthority);
        assertThat(config.getPrefixes()).isEmpty();

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

    @Test
    public void should_replace_roa_prefix() {
        var configuration = new RoaConfiguration(certificateAuthority);
        subject.handle(new UpdateRoaConfigurationCommand(
                certificateAuthority.getVersionedId(),
                Optional.of(configuration.convertToData().entityTag()),
                Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, null)),
                Collections.emptyList()));

        subject.handle(new UpdateRoaConfigurationCommand(
                certificateAuthority.getVersionedId(),
                Optional.of(roaConfigurationRepository.getOrCreateByCertificateAuthority(certificateAuthority).convertToData().entityTag()),
                Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX2, null)),
                Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, null))));

        var config = roaConfigurationRepository.getOrCreateByCertificateAuthority(certificateAuthority);
        assertThat(config.getPrefixes()).hasSize(1);
        RoaConfigurationPrefix p = config.getPrefixes().iterator().next();
        assertThat(p.getAsn()).isEqualTo(ASN);
        assertThat(p.getPrefix()).isEqualTo(PREFIX2);
        assertThat(p.getMaximumLength()).isEqualTo(12);
        assertThat(p.getUpdatedAt()).isNotNull();
    }

    @Test
    public void should_replace_roa_max_len() {
        var configuration = new RoaConfiguration(certificateAuthority);
        subject.handle(new UpdateRoaConfigurationCommand(
                certificateAuthority.getVersionedId(),
                Optional.of(configuration.convertToData().entityTag()),
                Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, null)),
                Collections.emptyList()));

        subject.handle(new UpdateRoaConfigurationCommand(
                certificateAuthority.getVersionedId(),
                Optional.of(roaConfigurationRepository.getOrCreateByCertificateAuthority(certificateAuthority).convertToData().entityTag()),
                Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, 17)),
                Collections.singletonList(new RoaConfigurationPrefixData(ASN, PREFIX1, null))));

        var config = roaConfigurationRepository.getOrCreateByCertificateAuthority(certificateAuthority);
        assertThat(config.getPrefixes()).hasSize(1);
        RoaConfigurationPrefix p = config.getPrefixes().iterator().next();
        assertThat(p.getAsn()).isEqualTo(ASN);
        assertThat(p.getPrefix()).isEqualTo(PREFIX1);
        assertThat(p.getMaximumLength()).isEqualTo(17);
        assertThat(p.getUpdatedAt()).isNotNull();
    }
}
