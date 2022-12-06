package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import static net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateTest.createSelfSignedCaResourceCertificateBuilder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class RoaEntityServiceBeanTest  {
    private static final Asn ASN = Asn.parse("AS123");
    private static final IpRange RESOURCE_1 = IpRange.parse("10.0.0.0/8");
    private static final IpRange RESOURCE_2 = IpRange.parse("192.168.0.0/24");
    private static final RoaConfigurationPrefix ROA_PREFIX_1 = new RoaConfigurationPrefix(ASN, RESOURCE_1, 16);
    private static final RoaConfigurationPrefix ROA_PREFIX_2 = new RoaConfigurationPrefix(ASN, RESOURCE_2, null);

    private ManagedCertificateAuthority ca;

    private SingleUseEeCertificateFactory singleUseEeCertificateFactory
        ;

    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private RoaConfigurationRepository roaConfigurationRepository;

    @Mock
    private RoaEntityRepository roaEntityRepository;

    private RoaEntityServiceBean subject;

    private RoaConfiguration configuration;

    @Before
    public void setUp() {
        singleUseEeCertificateFactory = TestServices.createSingleUseEeCertificateFactory();
        ca = TestObjects.createInitialisedProdCaWithRipeResources();
        configuration = new RoaConfiguration(ca, Arrays.asList(ROA_PREFIX_1, ROA_PREFIX_2));

        createAndInitSubject();
    }

    private void createAndInitSubject() {
        initMocks();
        subject = new RoaEntityServiceBean(certificateAuthorityRepository, roaConfigurationRepository, roaEntityRepository,
                new SingleUseKeyPairFactory(PregeneratedKeyPairFactory.getInstance()), singleUseEeCertificateFactory);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void should_create_roa_entity_for_roa_configuration() {
        RoaEntity result = handleRoaSpecificationCreatedEvent().getAddedRoa();
        assertNotNull(result);
    }

    @Test
    public void should_generate_non_publishable_ee_certificate_for_new_roa() {
        RoaEntity result = handleRoaSpecificationCreatedEvent().getAddedRoa();
        assertFalse(result.getCertificate().isPublishable());
    }

    @Test
    public void should_add_sia_to_roa_ee_certificate() {
        RoaEntity result = handleRoaSpecificationCreatedEvent().getAddedRoa();
        X509CertificateInformationAccessDescriptor[] sia = result.getCertificate().getSia();
        assertEquals(1, sia.length);
        assertTrue(sia[0].getLocation().toString().endsWith(".roa"));
        assertTrue(sia[0].getLocation().toString().startsWith("rsync://"));
    }

    @Test
    public void should_not_create_roa_entity_without_resources() {
        configuration.setPrefixes(Collections.singletonList(new RoaConfigurationPrefix(ASN, IpRange.parse("127.16.0.0/12"), 12)));
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        assertNull(roaEntity);
    }

    @Test
    public void should_not_create_expired_roa_entity() {
        DateTimeUtils.setCurrentMillisFixed(new DateTime().plusYears(3).getMillis());
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        assertNull(roaEntity);
    }

    @Test
    public void should_create_new_roa_entity_from_updated_specification() {
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();

        configuration.addPrefix(Collections.singleton(new RoaConfigurationPrefix(ASN, IpRange.parse("192.168.0.0/26"))));

        RoaEntity newRoaEntity = handleRoaSpecificationUpdatedEvent(roaEntity).getAddedRoa();

        assertNotNull(newRoaEntity);
        assertNotSame(roaEntity, newRoaEntity);
    }

    @Test
    public void should_revoke_invalidated_roa_on_specification_update() {
        RoaEntity oldRoa = handleRoaSpecificationCreatedEvent().getAddedRoa();

        configuration.addPrefix(Collections.singleton(new RoaConfigurationPrefix(ASN, IpRange.parse("192.168.0.0/26"))));

        RoaEntity removedRoa = handleRoaSpecificationUpdatedEvent(oldRoa).getRemovedRoa();

        assertSame(removedRoa, oldRoa);
        assertTrue(oldRoa.getCertificate().isRevoked());
        assertEquals(PublicationStatus.WITHDRAWN, oldRoa.getPublishedObject().getStatus());
    }

    @Test
    public void should_remove_roa_entities_when_no_longer_allowed_by_configuration() {
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        configuration.setPrefixes(Collections.emptyList());

        RoaEntity removedRoaEntity = handleRoaSpecificationRemovedEvent(roaEntity).getRemovedRoa();

        assertNotNull("original", roaEntity);
        assertNotNull("removed", removedRoaEntity);
        assertSame(roaEntity, removedRoaEntity);
    }

    @Test
    public void should_create_new_roa_entity_on_parent_certificate_publication_LocationChange() {
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        assertNotNull(roaEntity);

        ca.processCertificateIssuanceResponse(new CertificateIssuanceResponse(
            ca.getCurrentIncomingCertificate().getCertificate(), URI.create("rsync://updated/location.cer")),
            resourceCertificateRepository);

        RoaEntity addedRoa = updateAndRevokeRoas(roaEntity).getAddedRoa();

        assertNotNull("no new ROA created when parent certificate publication location changed", addedRoa);
        assertEquals("parent location", URI.create("rsync://updated/location.cer"), addedRoa.getRoaCms().getParentCertificateUri());
    }

    @Test
    public void should_ignore_incoming_certificate_changes_for_non_active_key() {
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        assertNotNull(roaEntity);

        RoaEntity addedRoa = updateAndRevokeRoas(roaEntity).getAddedRoa();
        assertNull(addedRoa);
    }

    @Test
    public void should_revoke_invalidated_roa_on_incoming_certificate_update() {
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();

        IncomingResourceCertificate old = ca.getCurrentIncomingCertificate();

        X509ResourceCertificate certificate = createSelfSignedCaResourceCertificateBuilder()
                .withSerial(BigInteger.valueOf(10000L))
                .withValidityPeriod(new ValidityPeriod(old.getNotValidBefore(), old.getNotValidAfter().minusDays(100)))
                .withPublicKey(ca.getCurrentKeyPair().getPublicKey())
                .withResources(new IpResourceSet(ca.getCertifiedResources()))
                .build();
        ca.processCertificateIssuanceResponse(new CertificateIssuanceResponse(certificate, TestObjects.PUBLICATION_URI), resourceCertificateRepository);

        RoaEntity addedRoa = updateAndRevokeRoas(roaEntity).getAddedRoa();
        assertNotNull(addedRoa);
        assertTrue(roaEntity.isRevoked());
        assertEquals(PublicationStatus.WITHDRAWN, roaEntity.getPublishedObject().getStatus());
    }

    @Test
    public void should_not_republish_the_old_roa_but_publish_a_new_one_after_previous_one_was_revoked() {
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        roaEntity.revokeAndRemove(roaEntityRepository);

        subject.updateRoasIfNeeded(ca);

        assertEquals(PublicationStatus.WITHDRAWN, roaEntity.getPublishedObject().getStatus());

        ArgumentCaptor<RoaEntity> added = ArgumentCaptor.forClass(RoaEntity.class);
        verify(roaEntityRepository, atMost(2)).add(added.capture());
        assertEquals(PublicationStatus.TO_BE_PUBLISHED, added.getValue().getPublishedObject().getStatus());
    }

    @Test
    public void should_skip_prefix_in_roa_configuration_if_not_covered_by_ca_resources() {
        IpRange notCoveredPrefix = IpRange.parse("3/16");
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        assertNotNull(roaEntity);

        configuration.addPrefix(Collections.singletonList(new RoaConfigurationPrefix(ASN, notCoveredPrefix, 24)));
        RoaEntity newRoaEntity = handleRoaSpecificationUpdatedEvent(roaEntity).getAddedRoa();

        assertNull(newRoaEntity);
    }

    @Test
    public void should_revoke_and_remove_unparsable_roa() throws Exception {
        RoaEntity roaEntity = handleRoaSpecificationCreatedEvent().getAddedRoa();
        PublishedObject publishedObject = roaEntity.getPublishedObject();

        Field contentField = GenericPublishedObject.class.getDeclaredField("content");
        contentField.setAccessible(true);
        byte[] content = (byte[]) contentField.get(publishedObject);
        content[38] = 0x55;

        when(roaEntityRepository.findByCertificateSigningKeyPair(isA(KeyPairEntity.class))).thenReturn(Collections.singletonList(roaEntity));

        subject.updateRoasIfNeeded(ca);

        assertEquals(PublicationStatus.WITHDRAWN, publishedObject.getStatus());
        assertTrue(roaEntity.getCertificate().isRevoked());
        verify(roaEntityRepository).remove(roaEntity);
    }

    private RoaSpecificationChangeResult handleRoaSpecificationCreatedEvent() {
        reset(roaEntityRepository);

        subject.updateRoasIfNeeded(ca);

        ArgumentCaptor<RoaEntity> added = ArgumentCaptor.forClass(RoaEntity.class);
        verify(roaEntityRepository, atMost(1)).add(added.capture());

        return new RoaSpecificationChangeResult(added.getAllValues().isEmpty() ? null : added.getValue(), null);
    }

    private RoaSpecificationChangeResult handleRoaSpecificationRemovedEvent(RoaEntity existingRoa) {
        return handleRoaSpecificationUpdatedEvent(existingRoa);
    }

    private RoaSpecificationChangeResult handleRoaSpecificationUpdatedEvent(RoaEntity existingRoa) {
        reset(roaEntityRepository);
        when(roaEntityRepository.findByCertificateSigningKeyPair(isA(KeyPairEntity.class))).thenReturn(Arrays.asList(existingRoa));

        subject.updateRoasIfNeeded(ca);

        ArgumentCaptor<RoaEntity> added = ArgumentCaptor.forClass(RoaEntity.class);
        verify(roaEntityRepository, atMost(1)).add(added.capture());

        ArgumentCaptor<RoaEntity> removed = ArgumentCaptor.forClass(RoaEntity.class);
        verify(roaEntityRepository, atMost(1)).remove(removed.capture());

        return new RoaSpecificationChangeResult(added.getAllValues().isEmpty() ? null : added.getValue(), removed.getAllValues().isEmpty() ? null : removed.getValue());
    }

    private RoaSpecificationChangeResult updateAndRevokeRoas(RoaEntity existingRoa) {
        reset(roaEntityRepository);
        when(roaEntityRepository.findByCertificateSigningKeyPair(isA(KeyPairEntity.class))).thenReturn(Arrays.asList(existingRoa));

        subject.updateRoasIfNeeded(ca);

        ArgumentCaptor<RoaEntity> added = ArgumentCaptor.forClass(RoaEntity.class);
        verify(roaEntityRepository, atMost(1)).add(added.capture());
        ArgumentCaptor<RoaEntity> removed = ArgumentCaptor.forClass(RoaEntity.class);
        verify(roaEntityRepository, atMost(1)).remove(removed.capture());

        return new RoaSpecificationChangeResult(added.getAllValues().isEmpty() ? null : added.getValue(), removed.getAllValues().isEmpty() ? null : removed.getValue());
    }

    private void initMocks() {
        when(roaConfigurationRepository.getOrCreateByCertificateAuthority(ca)).thenReturn(configuration);
    }

    private static final class RoaSpecificationChangeResult {
        private RoaEntity addedRoa;
        private RoaEntity removedRoa;

        public RoaSpecificationChangeResult(RoaEntity addedRoa, RoaEntity removedRoa) {
            this.addedRoa = addedRoa;
            this.removedRoa = removedRoa;
        }

        public RoaEntity getAddedRoa() {
            return addedRoa;
        }

        public RoaEntity getRemovedRoa() {
            return removedRoa;
        }
    }
}
