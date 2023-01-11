package net.ripe.rpki.domain.aspa;

import com.google.common.collect.ImmutableSortedSet;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.cms.aspa.AspaCms;
import net.ripe.rpki.commons.crypto.cms.aspa.ProviderAS;
import net.ripe.rpki.commons.crypto.rfc3779.AddressFamily;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.core.events.KeyPairActivatedEvent;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.SingleUseKeyPairFactory;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.audit.CommandAudit;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.server.api.commands.CommandContext;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.dto.AspaAfiLimit;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.net.URI;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateTest.createSelfSignedCaResourceCertificateBuilder;
import static net.ripe.rpki.domain.TestObjects.CA_ID;
import static net.ripe.rpki.domain.TestObjects.PRODUCTION_CA_NAME;
import static net.ripe.rpki.domain.TestObjects.TEST_VALIDITY_PERIOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AspaEntityServiceBeanTest {

    public static final Asn CUSTOMER_ASN = Asn.parse("AS21212");
    public static final Asn ASN_1 = Asn.parse("AS1");
    public static final Asn ASN_2 = Asn.parse("AS2");
    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private AspaEntityRepository aspaEntityRepository;
    @Mock
    private AspaConfigurationRepository aspaConfigurationRepository;

    private ManagedCertificateAuthority certificateAuthority;
    private KeyPairEntity oldKeyPair;
    private KeyPairEntity activeKeyPair;
    private AspaEntity aspaEntity;
    private AspaEntityServiceBean subject;

    @Before
    public void setUp() {
        DateTimeUtils.setCurrentMillisFixed(TEST_VALIDITY_PERIOD.getNotValidBefore().getMillis() + 1000);
        subject = new AspaEntityServiceBean(certificateAuthorityRepository, aspaConfigurationRepository, aspaEntityRepository, new SingleUseKeyPairFactory(), TestServices.createSingleUseEeCertificateFactory());

        certificateAuthority = new ProductionCertificateAuthority(CA_ID, PRODUCTION_CA_NAME, UUID.randomUUID(), null);
        oldKeyPair = TestObjects.createActiveKeyPair("OLD");
        certificateAuthority.addKeyPair(oldKeyPair);
        oldKeyPair.deactivate();
        activeKeyPair = TestObjects.createActiveKeyPair("ACTIVE");
        certificateAuthority.addKeyPair(activeKeyPair);

        when(certificateAuthorityRepository.findManagedCa(CA_ID)).thenReturn(certificateAuthority);

        AspaConfiguration aspaConfiguration = new AspaConfiguration(certificateAuthority, CUSTOMER_ASN, Collections.singletonMap(ASN_1, AspaAfiLimit.ANY));
        when(aspaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(new TreeMap<>(Collections.singletonMap(CUSTOMER_ASN, aspaConfiguration)));

        aspaEntity = subject.createAspaEntity(certificateAuthority, new AspaConfiguration(certificateAuthority, CUSTOMER_ASN, Collections.singletonMap(ASN_1, AspaAfiLimit.ANY)));
        when(aspaEntityRepository.findCurrentByCertificateAuthority(certificateAuthority)).thenReturn(Collections.singletonList(aspaEntity));
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void should_remove_aspa_entities_signed_by_old_key_on_activation_of_new_key() {
        when(aspaEntityRepository.findByCertificateSigningKeyPair(oldKeyPair)).thenReturn(Collections.singletonList(aspaEntity));

        subject.visitKeyPairActivatedEvent(
            new KeyPairActivatedEvent(certificateAuthority.getVersionedId(), activeKeyPair),
            new CommandContext(KeyManagementActivatePendingKeysCommand.manualActivationCommand(certificateAuthority.getVersionedId()), mock(CommandAudit.class))
        );

        assertThat(aspaEntity.isRevoked()).isTrue();
        verify(aspaEntityRepository).remove(aspaEntity);
    }

    @Test
    public void should_create_aspa_entity_when_aspa_configuration_for_new_customer_asn_is_added() {
        AspaConfiguration aspaConfiguration = new AspaConfiguration(certificateAuthority, CUSTOMER_ASN, Collections.singletonMap(ASN_2, AspaAfiLimit.IPv4));
        when(aspaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(new TreeMap<>(Collections.singletonMap(CUSTOMER_ASN, aspaConfiguration)));

        subject.updateAspaIfNeeded(certificateAuthority);

        ArgumentCaptor<AspaEntity> aspaEntityArgumentCaptor = ArgumentCaptor.forClass(AspaEntity.class);
        verify(aspaEntityRepository).add(aspaEntityArgumentCaptor.capture());

        AspaCms aspaCms = aspaEntityArgumentCaptor.getValue().getAspaCms();
        assertThat(aspaCms.getCustomerAsn()).isEqualTo(CUSTOMER_ASN);
        assertThat(aspaCms.getProviderASSet()).isEqualTo(ImmutableSortedSet.of(new ProviderAS(ASN_2, Optional.of(AddressFamily.IPV4))));
    }

    @Test
    public void should_skip_customer_asn_in_configuration_if_not_covered_by_ca_certified_resource() {
        AspaConfiguration aspaConfiguration = new AspaConfiguration(certificateAuthority, ASN_1, Collections.singletonMap(ASN_2, AspaAfiLimit.IPv4));
        when(aspaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(new TreeMap<>(Collections.singletonMap(ASN_1, aspaConfiguration)));

        subject.updateAspaIfNeeded(certificateAuthority);

        verify(aspaEntityRepository, never()).add(any());
    }

    @Test
    public void should_revoke_and_remove_aspa_entity_when_not_configured_for_customer_asn() {
        when(aspaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(new TreeMap<>());

        subject.updateAspaIfNeeded(certificateAuthority);

        assertThat(aspaEntity.isRevoked()).isTrue();
        verify(aspaEntityRepository).remove(aspaEntity);
    }

    @Test
    public void should_revoke_and_remove_old_aspa_entity_and_create_new_aspa_entity_when_providers_changed() {
        AspaConfiguration aspaConfiguration = new AspaConfiguration(certificateAuthority, CUSTOMER_ASN, Collections.singletonMap(ASN_2, AspaAfiLimit.IPv4));
        when(aspaConfigurationRepository.findByCertificateAuthority(certificateAuthority)).thenReturn(new TreeMap<>(Collections.singletonMap(CUSTOMER_ASN, aspaConfiguration)));

        subject.updateAspaIfNeeded(certificateAuthority);

        assertThat(aspaEntity.isRevoked()).isTrue();
        verify(aspaEntityRepository).remove(aspaEntity);
        ArgumentCaptor<AspaEntity> aspaEntityArgumentCaptor = ArgumentCaptor.forClass(AspaEntity.class);
        verify(aspaEntityRepository).add(aspaEntityArgumentCaptor.capture());

        AspaCms aspaCms = aspaEntityArgumentCaptor.getValue().getAspaCms();
        assertThat(aspaCms.getCustomerAsn()).isEqualTo(CUSTOMER_ASN);
        assertThat(aspaCms.getProviderASSet()).isEqualTo(ImmutableSortedSet.of(new ProviderAS(ASN_2, Optional.of(AddressFamily.IPV4))));
    }

    @Test
    public void should_reissue_aspas_when_parent_certificate_location_changed() {
        activeKeyPair.getCurrentIncomingCertificate().setPublicationUri(URI.create("rsync://rsync.example.com/new/publication/uri.cer"));

        subject.updateAspaIfNeeded(certificateAuthority);

        assertThat(aspaEntity.isRevoked()).isTrue();
        verify(aspaEntityRepository).remove(aspaEntity);

        ArgumentCaptor<AspaEntity> aspaEntityArgumentCaptor = ArgumentCaptor.forClass(AspaEntity.class);
        verify(aspaEntityRepository).add(aspaEntityArgumentCaptor.capture());
        AspaEntity updatedAspaEntity = aspaEntityArgumentCaptor.getValue();
        assertThat(updatedAspaEntity.getCustomerAsn()).isEqualTo(aspaEntity.getCustomerAsn());
        assertThat(updatedAspaEntity.getProviders()).isEqualTo(aspaEntity.getProviders());
    }

    @Test
    public void should_reissue_aspas_when_ee_certificate_is_not_valid() {
        aspaEntity.getCertificate().revoke();

        subject.updateAspaIfNeeded(certificateAuthority);

        ArgumentCaptor<AspaEntity> aspaEntityArgumentCaptor = ArgumentCaptor.forClass(AspaEntity.class);
        verify(aspaEntityRepository).add(aspaEntityArgumentCaptor.capture());
        AspaEntity updatedAspaEntity = aspaEntityArgumentCaptor.getValue();
        assertThat(updatedAspaEntity.getCustomerAsn()).isEqualTo(aspaEntity.getCustomerAsn());
        assertThat(updatedAspaEntity.getProviders()).isEqualTo(aspaEntity.getProviders());
    }

    @Test
    public void should_revoke_aspa_when_customer_asn_is_no_longer_certified() {
        IncomingResourceCertificate currentIncomingCertificate = certificateAuthority.getCurrentIncomingCertificate();
        X509ResourceCertificate certificate = createSelfSignedCaResourceCertificateBuilder()
            .withSerial(BigInteger.valueOf(10000L))
            .withValidityPeriod(currentIncomingCertificate.getValidityPeriod())
            .withPublicKey(certificateAuthority.getCurrentKeyPair().getPublicKey())
            .withResources(new IpResourceSet(ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES))
            .build();
        certificateAuthority.processCertificateIssuanceResponse(new CertificateIssuanceResponse(certificate, TestObjects.PUBLICATION_URI), null);

        subject.updateAspaIfNeeded(certificateAuthority);

        assertThat(aspaEntity.isRevoked()).isTrue();
        verify(aspaEntityRepository).remove(aspaEntity);

        verify(aspaEntityRepository, never()).add(any());
    }

    @Test
    public void should_leave_correct_aspas_unmodified() {
        subject.updateAspaIfNeeded(certificateAuthority);

        assertThat(aspaEntity.isRevoked()).isFalse();
        verify(aspaEntityRepository, never()).remove(aspaEntity);
        verify(aspaEntityRepository, never()).add(any());
    }
}
