package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsTest;
import net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix;
import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.KeyPairEntityTest;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.OutgoingResourceCertificateTest;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.TestServices;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.naming.UuidRepositoryObjectNamingStrategy;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoaEntityTest {
    private static final KeyPair ROA_KEY_PAIR = KeyPairFactoryTest.SECOND_TEST_KEY_PAIR;
    private static final X509CertificateInformationAccessDescriptor[] SIA = {
            new X509CertificateInformationAccessDescriptor(
                    X509CertificateInformationAccessDescriptor.ID_AD_SIGNED_OBJECT,
                    KeyPairEntityTest.TEST_REPOSITORY_LOCATION.resolve("filename.roa"))
    };

    private static final IpRange RESOURCE_1 = IpRange.parse("10.0.0.0/8");
    private static final IpRange RESOURCE_2 = IpRange.parse("192.168.0.0/24");

    private OutgoingResourceCertificate certificate;
    private RoaCms roaCms;
    private RoaEntity subject;

    @Before
    public void setUp() {
        IpResourceSet resources = new IpResourceSet(RESOURCE_1, RESOURCE_2);
        certificate = OutgoingResourceCertificateTest.createBuilder(TestObjects.TEST_KEY_PAIR_2, TestObjects.TEST_KEY_PAIR_2.getPublicKey()).withSubjectPublicKey(ROA_KEY_PAIR.getPublic()).withResources(resources).withSubjectInformationAccess(SIA).build();
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
        subject.revoke();
        assertTrue(subject.isRevoked());
        assertEquals(PublicationStatus.WITHDRAWN, subject.getPublishedObject().getStatus());
    }

    public static RoaEntity createEeSignedRoaEntity(ManagedCertificateAuthority ca, String roaName, KeyPairEntity keyPair, ValidityPeriod validityPeriod) {
        IpRange roaPrefix = IpRange.parse("10.0.0.0/8");
        CertificateIssuanceRequest request = new CertificateIssuanceRequest(
            new IpResourceSet(roaPrefix),
            new UuidRepositoryObjectNamingStrategy().eeCertificateSubject(roaName, keyPair.getPublicKey(), ca.getCurrentKeyPair()),
            keyPair.getPublicKey(),
            SIA
        );
        OutgoingResourceCertificate roaCert = TestServices.createCertificateManagementService().issueSingleUseEeResourceCertificate(
                ca, request, validityPeriod, ca.getCurrentKeyPair());
        return createRoaEntity(roaCert, Collections.singletonList(new RoaPrefix(roaPrefix, null)));
    }

    public static RoaEntity createRoaEntity(OutgoingResourceCertificate eeCertificate, List<RoaPrefix> prefixes) {
        RoaCms roaCms = RoaCmsTest.createRoaCms(prefixes);
        return new RoaEntity(eeCertificate, roaCms, "filename.roa", KeyPairEntityTest.TEST_REPOSITORY_LOCATION);
    }
}
