package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;

import static net.ripe.rpki.domain.TestObjects.TEST_VALIDITY_PERIOD;
import static org.junit.Assert.assertFalse;

@Transactional
public class ProductionCertificateAuthorityTest extends CertificationDomainTestCase {
    private ProductionCertificateAuthority prodCa;
    private KeyPairEntity kp;

    @Before
    public void setUp() {
        prodCa = new ProductionCertificateAuthority(12, TestObjects.PRODUCTION_CA_NAME, null);
        kp = TestObjects.createActiveKeyPair("TEST-KEY");
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test(expected = CertificateAuthorityException.class)
    public void shouldVerifyResourcesWhenIssuingEndEntityResourceCertificate() {

        KeyPairEntity kp = TestObjects.createTestKeyPair("TEST-KEY");
        prodCa.addKeyPair(kp);

        IncomingResourceCertificate currentCertificate = TestObjects.createResourceCertificate(
            123L,
            kp,
            new ValidityPeriod(new DateTime().minusYears(2), new DateTime().plusYears(5).plusMinutes(1)),
            ImmutableResourceSet.parse("10/8"),
            createSia()
        );
        kp.updateIncomingResourceCertificate(currentCertificate.getCertificate(), currentCertificate.getPublicationUri());

        CertificateIssuanceRequest request = new CertificateIssuanceRequest(ImmutableResourceSet.parse("11.0.0.0/8"), new X500Principal("CN=test"), kp.getPublicKey(), createSia());
        singleUseEeCertificateFactory.issueSingleUseEeResourceCertificate(request, TEST_VALIDITY_PERIOD, prodCa.getCurrentKeyPair());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToRemoveSignedKeyPair() {
        KeyPairEntity kp = TestObjects.createTestKeyPair("TEST-KEY");
        kp.activate();
        prodCa.addKeyPair(kp);

        assertFalse(kp.isRemovable());
        prodCa.removeKeyPair(kp);
    }

    private X509CertificateInformationAccessDescriptor[] createSia() {
        return new X509CertificateInformationAccessDescriptor[]{
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                TestObjects.BASE_URI),
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                TestObjects.BASE_URI.resolve(kp.getManifestFilename())),
        };
    }

}
