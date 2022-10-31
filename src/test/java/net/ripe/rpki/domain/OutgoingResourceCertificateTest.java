package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtensionEncoder;
import org.bouncycastle.asn1.x509.Extension;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class OutgoingResourceCertificateTest {

    private KeyPairEntity keyPair;
    private OutgoingResourceCertificate subject;

    @Before
    public void setUp() {
        keyPair = TestObjects.createActiveKeyPair("KEY");
        subject = TestObjects.createOutgoingResourceCertificate(TestObjects.TEST_SERIAL_NUMBER, keyPair, keyPair.getPublicKey(), TestObjects.TEST_VALIDITY_PERIOD, TestObjects.TEST_RESOURCE_SET, TestObjects.SUBJECT_INFORMATION_ACCESS);
        DateTimeUtils.setCurrentMillisFixed(subject.getNotValidBefore().getMillis());
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void testSelfSignedResourceCertificate() {
        assertEquals(BigInteger.valueOf(TestObjects.TEST_SERIAL_NUMBER), subject.getSerial());
        assertEquals(TestObjects.TEST_RESOURCE_SET, subject.getResources());
        assertEquals(TestObjects.TEST_SELF_SIGNED_CERTIFICATE_NAME, subject.getSubject());
        assertEquals(TestObjects.TEST_SELF_SIGNED_CERTIFICATE_NAME, subject.getIssuer());
        assertEquals(TestObjects.TEST_VALIDITY_PERIOD, subject.getValidityPeriod());
        assertEquals(keyPair.getPublicKey(), subject.getSubjectPublicKey());
    }

    @Test
    public void testGenerateX509Certificate() throws Exception {
        X509Certificate x509Certificate = subject.getCertificate().getCertificate();

        x509Certificate.verify(keyPair.getPublicKey());

        assertEquals(TestObjects.TEST_SELF_SIGNED_CERTIFICATE_NAME, x509Certificate.getIssuerX500Principal());
        assertEquals(TestObjects.TEST_SELF_SIGNED_CERTIFICATE_NAME, x509Certificate.getSubjectX500Principal());
        assertEquals(BigInteger.valueOf(TestObjects.TEST_SERIAL_NUMBER), x509Certificate.getSerialNumber());
        assertEquals(keyPair.getPublicKey(), x509Certificate.getPublicKey());
        assertArrayEquals(new boolean[]{false, false, false, false, false, true, true, false, false}, x509Certificate.getKeyUsage());

        Set<String> critSet = x509Certificate.getCriticalExtensionOIDs();
        assertEquals(5, critSet.size());
        assertTrue(critSet.contains(ResourceExtensionEncoder.OID_IP_ADDRESS_BLOCKS.getId()));
        assertTrue(critSet.contains(ResourceExtensionEncoder.OID_AUTONOMOUS_SYS_IDS.getId()));
        assertTrue(critSet.contains(Extension.keyUsage.getId()));
        assertTrue(critSet.contains(Extension.basicConstraints.getId()));
        assertTrue(critSet.contains(Extension.certificatePolicies.getId()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRequireClosedValidityPeriod() {
        TestObjects.createResourceCertificate(TestObjects.TEST_SERIAL_NUMBER, keyPair, new ValidityPeriod(new DateTime(), null), TestObjects.TEST_RESOURCE_SET, TestObjects.SUBJECT_INFORMATION_ACCESS);
    }

    @Test
    public void shouldBeRevokable() {
        assertEquals(PublicationStatus.TO_BE_PUBLISHED, subject.getPublishedObject().getStatus());
        assertFalse(subject.isRevoked());

        subject.revoke();

        assertTrue(subject.isRevoked());
        assertEquals(PublicationStatus.WITHDRAWN, subject.getPublishedObject().getStatus());
    }

    @Test
    public void shouldOnlyUpdateRevocationTimeOnce() {
        DateTime now = new DateTime(DateTimeZone.UTC);

        subject.revoke();
        assertEquals(now, subject.getRevocationTime());

        DateTimeUtils.setCurrentMillisFixed(now.plusDays(2).getMillis());

        subject.revoke();
        assertEquals(now, subject.getRevocationTime());
    }

    @Test
    public void shouldOnlyRevokeWhenNotExpired() {
        expireSubject();

        assertFalse(subject.isRevoked());
        subject.revoke();

        assertFalse(subject.isRevoked());
    }

    @Test
    public void shouldNotBeExpiredUntilNotValidAfterDateTime() {
        DateTimeUtils.setCurrentMillisFixed(subject.getNotValidBefore().minusMonths(2).getMillis());
        assertFalse(subject.isExpired());

        DateTimeUtils.setCurrentMillisFixed(subject.getNotValidBefore().getMillis());
        assertFalse(subject.isExpired());

        DateTimeUtils.setCurrentMillisFixed(subject.getNotValidAfter().getMillis());
        assertFalse(subject.isExpired());

        expireSubject();
        assertTrue(subject.isExpired());
    }


    @Test
    public void shouldHaveInformationAccess() {
        assertArrayEquals(TestObjects.AUTHORITY_INFORMATION_ACCESS, subject.getAia());
        assertArrayEquals(TestObjects.SUBJECT_INFORMATION_ACCESS, subject.getSia());
    }

    @Test
    public void shouldNotBePublishableWhenExpired() {
        assertEquals(PublicationStatus.TO_BE_PUBLISHED, subject.getPublishedObject().getStatus());
        assertFalse(subject.isExpired());

        expireSubject();

        assertTrue(subject.isExpired());
        assertEquals(PublicationStatus.WITHDRAWN, subject.getPublishedObject().getStatus());
    }

    private void expireSubject() {
        DateTime now = subject.getNotValidAfter().plusMillis(1);
        subject.expire(now);
    }

}
