package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsTest;
import net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix;
import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.domain.KeyPairEntityTest;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.naming.UuidRepositoryObjectNamingStrategy;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RoaEntityTest {
    private static final KeyPair ROA_KEY_PAIR = KeyPairFactoryTest.SECOND_TEST_KEY_PAIR;

    private static final IpRange RESOURCE_1 = IpRange.parse("10.0.0.0/8");
    private static final IpRange RESOURCE_2 = IpRange.parse("192.168.0.0/24");

    private OutgoingResourceCertificate certificate;
    private RoaCms roaCms;
    private RoaEntity subject;
    private RoaEntityRepository roaEntityRepository;

    @Before
    public void setUp() {
        roaEntityRepository = mock(RoaEntityRepository.class);
        ImmutableResourceSet resources = ImmutableResourceSet.of(RESOURCE_1, RESOURCE_2);
        certificate = TestObjects.createBuilder(TestObjects.TEST_KEY_PAIR_2, TestObjects.TEST_KEY_PAIR_2.getPublicKey()).withSubjectPublicKey(ROA_KEY_PAIR.getPublic()).withResources(resources).withSubjectInformationAccess(TestObjects.EE_CERT_SIA).build();
        roaCms = createRoaEntity(certificate, Arrays.asList(new RoaPrefix(RESOURCE_1, 16), new RoaPrefix(RESOURCE_2, null))).getRoaCms();
        subject = new RoaEntity(certificate, roaCms, "filename.roa", KeyPairEntityTest.TEST_REPOSITORY_LOCATION);
    }

    @Test
    public void shouldHaveCertificate() {
        assertEquals(certificate, subject.getCertificate());
    }

    @Test
    public void shouldHaveRoaCms() {
        assertEquals(roaCms, subject.getRoaCms());
    }

    @Test
    public void canBeRevoked() {
        assertFalse(subject.isRevoked());
        subject.revokeAndRemove(roaEntityRepository);
        assertTrue(subject.isRevoked());
        assertEquals(PublicationStatus.WITHDRAWN, subject.getPublishedObject().getStatus());
        verify(roaEntityRepository).remove(subject);
    }

    public static RoaEntity createEeSignedRoaEntity(ManagedCertificateAuthority ca, PublicKey subjectPublicKey, ValidityPeriod validityPeriod) {
        IpRange roaPrefix = IpRange.parse("10.0.0.0/8");
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(
            ImmutableResourceSet.of(roaPrefix),
            new UuidRepositoryObjectNamingStrategy().eeCertificateSubject(subjectPublicKey),
            subjectPublicKey,
            TestObjects.EE_CERT_SIA
        );
        OutgoingResourceCertificate roaCert = TestServices.createSingleUseEeCertificateFactory().issueSingleUseEeResourceCertificate(
            request, validityPeriod, ca.getCurrentKeyPair());
        return createRoaEntity(roaCert, Collections.singletonList(new RoaPrefix(roaPrefix, null)));
    }

    public static RoaEntity createRoaEntity(OutgoingResourceCertificate eeCertificate, List<RoaPrefix> prefixes) {
        RoaCms roaCms = RoaCmsTest.createRoaCms(prefixes);
        return new RoaEntity(eeCertificate, roaCms, "filename.roa", KeyPairEntityTest.TEST_REPOSITORY_LOCATION);
    }
}
