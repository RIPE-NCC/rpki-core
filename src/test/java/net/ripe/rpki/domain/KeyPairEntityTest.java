package net.ripe.rpki.domain;

import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.signing.ChildCertificateSignerTest;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyPairEntityTest {

    public static final URI TEST_REPOSITORY_LOCATION = URI.create("rsync://repository/location/");
    public static final String TEST_KEY_PAIR_NAME = "FOR-TESTING-ONLY";

    @Mock
    private ChildCertificateAuthority requestingCa;

    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;

    @Rule
    public FixedDateRule fixedDateRule = new FixedDateRule(new DateTime(DateTimeZone.UTC));

    private DateTime now;

    @Before
    public void setUp() {
        now = new DateTime(DateTimeZone.UTC);
    }

    @Test
    public void testNewRootKey() {
        KeyPairEntity keyPair = TestObjects.createTestKeyPair();

        assertTrue(keyPair.getPrivateKey().getEncoded().length > 0);
        assertTrue(keyPair.getPublicKey().getEncoded().length > 0);
        assertEquals(KeyPairFactory.ALGORITHM, keyPair.getAlgorithm());
        assertEquals(KeyPairStatus.PENDING, keyPair.getStatus());
    }

    @Test
    public void should_track_activated_at() {
        KeyPairEntity subject = TestObjects.createTestKeyPair();
        IncomingResourceCertificate certificate = TestObjects.createResourceCertificate(12L, subject);
        subject.updateIncomingResourceCertificate(certificate.getCertificate(), certificate.getPublicationUri());
        assertNull("key pair not yet activated", subject.getStatusChangedAt(KeyPairStatus.CURRENT));

        subject.activate();

        assertEquals(now, subject.getStatusChangedAt(KeyPairStatus.CURRENT));
    }

    @Test
    public void should_track_deactivated_at() {
        KeyPairEntity subject = TestObjects.createActiveKeyPair(TEST_KEY_PAIR_NAME);
        assertNull("key pair not yet deactivated", subject.getStatusChangedAt(KeyPairStatus.OLD));

        subject.deactivate();

        assertEquals(now, subject.getStatusChangedAt(KeyPairStatus.OLD));
    }

    @Test
    public void shouldSignChildCertificate() {
        KeyPairEntity subject = TestObjects.createActiveKeyPair(TEST_KEY_PAIR_NAME);
        CertificateIssuanceResponse response = subject.processCertificateIssuanceRequest(requestingCa, ChildCertificateSignerTest.TEST_REQUEST, BigInteger.TEN, resourceCertificateRepository);

        assertNotNull(response);
        assertNotNull(response.getCertificate());
    }

    @Test
    public void shouldRevokeOldCertificatesWhenSigningChildCertificate() {
        KeyPairEntity subject = TestObjects.createActiveKeyPair(TEST_KEY_PAIR_NAME);
        OutgoingResourceCertificate oldCertificate = mock(OutgoingResourceCertificate.class);
        when(resourceCertificateRepository.findCurrentCertificatesBySubjectPublicKey(ChildCertificateSignerTest.TEST_REQUEST.getSubjectPublicKey())).thenReturn(Collections.singletonList(oldCertificate));

        subject.processCertificateIssuanceRequest(requestingCa, ChildCertificateSignerTest.TEST_REQUEST, BigInteger.TEN, resourceCertificateRepository);

        verify(oldCertificate).revoke();
    }
}
