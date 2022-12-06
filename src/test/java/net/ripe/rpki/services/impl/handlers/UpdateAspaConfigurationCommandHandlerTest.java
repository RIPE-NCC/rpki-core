package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.server.api.commands.UpdateAspaConfigurationCommand;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;
import net.ripe.rpki.server.api.dto.AspaProviderData;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.server.api.services.command.DuplicateResourceException;
import net.ripe.rpki.server.api.services.command.EntityTagDoesNotMatchException;
import net.ripe.rpki.server.api.services.command.NotHolderOfResourcesException;
import net.ripe.rpki.server.api.services.command.PrivateAsnsUsedException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.util.Collections.singletonList;
import static net.ripe.rpki.domain.TestObjects.CA_ID;
import static net.ripe.rpki.domain.aspa.AspaConfiguration.entitiesToMaps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpdateAspaConfigurationCommandHandlerTest {
    private static final String PRIVATE_ASNS = "64512-65535, 4200000000-4294967294";
    private static final String EMPTY_CONFIGURATION_ETAG = AspaConfigurationData.entityTag(Collections.emptySortedMap());

    private AspaConfigurationRepository aspaConfigurationRepository;
    private ManagedCertificateAuthority managedCertificateAuthority;
    private UpdateAspaConfigurationCommandHandler subject;

    @Before
    public void setUp() {
        CertificateAuthorityRepository certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        aspaConfigurationRepository = mock(AspaConfigurationRepository.class);
        managedCertificateAuthority = mock(ManagedCertificateAuthority.class);

        when(certificateAuthorityRepository.findManagedCa(CA_ID)).thenReturn(managedCertificateAuthority);
        when(managedCertificateAuthority.getCertifiedResources()).thenReturn(ImmutableResourceSet.parse("AS1234"));

        subject = new UpdateAspaConfigurationCommandHandler(certificateAuthorityRepository, aspaConfigurationRepository, PRIVATE_ASNS);
    }

    @Test
    public void should_add_new_customer_asn() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), EMPTY_CONFIGURATION_ETAG, singletonList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            singletonList(new AspaProviderData(Asn.parse("AS3"), AspaAfiLimit.IPv4))
        )));

        subject.handle(command);

        ArgumentCaptor<AspaConfiguration> aspaConfigurationArgumentCaptor = ArgumentCaptor.forClass(AspaConfiguration.class);
        verify(aspaConfigurationRepository).add(aspaConfigurationArgumentCaptor.capture());
        assertThat(aspaConfigurationArgumentCaptor.getValue().getProviders()).isEqualTo(Collections.singletonMap(Asn.parse("AS3"), AspaAfiLimit.IPv4));
        verify(managedCertificateAuthority).configurationUpdated();
    }

    @Test
    public void should_replace_provider_asns_for_matching_customer_asn() {
        AspaConfiguration aspa_as1234 = new AspaConfiguration(managedCertificateAuthority, Asn.parse("AS1234"), Collections.singletonMap(Asn.parse("AS1"), AspaAfiLimit.ANY));
        SortedMap<Asn, AspaConfiguration> aspaCo = new TreeMap<>();
        aspaCo.put(aspa_as1234.getCustomerAsn(), aspa_as1234);
        when(aspaConfigurationRepository.findByCertificateAuthority(managedCertificateAuthority)).thenReturn(aspaCo);

        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), AspaConfigurationData.entityTag(entitiesToMaps(aspaCo)), singletonList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            singletonList(new AspaProviderData(Asn.parse("AS3"), AspaAfiLimit.IPv4))
        )));

        subject.handle(command);

        assertThat(aspa_as1234.getProviders()).isEqualTo(Collections.singletonMap(Asn.parse("AS3"), AspaAfiLimit.IPv4));
        verify(managedCertificateAuthority).configurationUpdated();
    }

    @Test
    public void should_remove_customer_asn() {
        AspaConfiguration aspa_as1234 = new AspaConfiguration(managedCertificateAuthority, Asn.parse("AS1234"), Collections.singletonMap(Asn.parse("AS1"), AspaAfiLimit.ANY));
        SortedMap<Asn, AspaConfiguration> aspaCo = new TreeMap<>();
        aspaCo.put(aspa_as1234.getCustomerAsn(), aspa_as1234);
        when(aspaConfigurationRepository.findByCertificateAuthority(managedCertificateAuthority)).thenReturn(aspaCo);

        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), AspaConfigurationData.entityTag(entitiesToMaps(aspaCo)), Collections.emptyList());

        subject.handle(command);

        verify(aspaConfigurationRepository).remove(aspa_as1234);
        verify(managedCertificateAuthority).configurationUpdated();
    }

    @Test
    public void should_have_no_effect_when_there_are_no_differences() {
        AspaConfiguration aspa_as1234 = new AspaConfiguration(managedCertificateAuthority, Asn.parse("AS1234"), Collections.singletonMap(Asn.parse("AS1"), AspaAfiLimit.ANY));
        SortedMap<Asn, AspaConfiguration> aspaCo = new TreeMap<>();
        aspaCo.put(aspa_as1234.getCustomerAsn(), aspa_as1234);
        when(aspaConfigurationRepository.findByCertificateAuthority(managedCertificateAuthority)).thenReturn(aspaCo);

        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), AspaConfigurationData.entityTag(entitiesToMaps(aspaCo)), singletonList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            singletonList(new AspaProviderData(Asn.parse("AS1"), AspaAfiLimit.ANY))
        )));

        assertThatThrownBy(() -> subject.handle(command)).isInstanceOf(CommandWithoutEffectException.class);
        verify(managedCertificateAuthority, never()).configurationUpdated();
    }

    @Test
    public void should_reject_customer_asn_if_not_certified() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), EMPTY_CONFIGURATION_ETAG, singletonList(new AspaConfigurationData(
            Asn.parse("AS9000"),
            singletonList(new AspaProviderData(Asn.parse("AS3"), AspaAfiLimit.IPv4))
        )));

        assertThatThrownBy(() -> subject.handle(command)).isInstanceOf(NotHolderOfResourcesException.class);
    }

    @Test
    public void should_reject_use_of_private_asns_in_providers() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), EMPTY_CONFIGURATION_ETAG, singletonList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            singletonList(new AspaProviderData(Asn.parse("AS64512"), AspaAfiLimit.ANY))
        )));

        assertThatThrownBy(() -> subject.handle(command)).isInstanceOf(PrivateAsnsUsedException.class);
    }

    @Test
    public void should_reject_duplicate_customer_asns() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), EMPTY_CONFIGURATION_ETAG, Arrays.asList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            singletonList(new AspaProviderData(Asn.parse("AS9"), AspaAfiLimit.ANY))
        ), new AspaConfigurationData(
            Asn.parse("AS1234"),
            singletonList(new AspaProviderData(Asn.parse("AS10"), AspaAfiLimit.ANY))
        )));

        assertThatThrownBy(() -> subject.handle(command)).isInstanceOfSatisfying(DuplicateResourceException.class, exception -> {
            assertThat(exception.getMessage()).isEqualTo("duplicate ASN in ASPA configuration");
        });
    }

    @Test
    public void should_reject_duplicate_provider_asns() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), EMPTY_CONFIGURATION_ETAG, singletonList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            Arrays.asList(new AspaProviderData(Asn.parse("AS9"), AspaAfiLimit.ANY), new AspaProviderData(Asn.parse("AS9"), AspaAfiLimit.IPv4))
        )));

        assertThatThrownBy(() -> subject.handle(command)).isInstanceOfSatisfying(DuplicateResourceException.class, exception -> {
            assertThat(exception.getMessage()).isEqualTo("duplicate ASN in ASPA configuration");
        });
    }

    @Test
    public void should_reject_aspa_when_customer_asn_appears_in_provider_asn() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), EMPTY_CONFIGURATION_ETAG, singletonList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            Collections.singletonList(new AspaProviderData(Asn.parse("AS1234"), AspaAfiLimit.ANY))
        )));

        assertThatThrownBy(() -> subject.handle(command)).isInstanceOfSatisfying(DuplicateResourceException.class, exception -> {
            assertThat(exception.getMessage()).isEqualTo("customer AS1234 appears in provider set [AS1234]");
        });
    }

    @Test
    public void should_reject_command_if_entity_tag_does_not_match_current_configuration() {
        UpdateAspaConfigurationCommand command = new UpdateAspaConfigurationCommand(new VersionedId(CA_ID), "no-match", singletonList(new AspaConfigurationData(
            Asn.parse("AS1234"),
            singletonList(new AspaProviderData(Asn.parse("AS1"), AspaAfiLimit.ANY))
        )));

        assertThatThrownBy(() -> subject.handle(command)).isInstanceOf(EntityTagDoesNotMatchException.class);
    }
}
