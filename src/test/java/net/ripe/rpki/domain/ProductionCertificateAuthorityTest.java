package net.ripe.rpki.domain;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationServiceBean;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.util.List;

import static net.ripe.ipresource.IpResourceSet.IP_PRIVATE_USE_RESOURCES;
import static net.ripe.rpki.domain.TestObjects.TEST_VALIDITY_PERIOD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Transactional
public class ProductionCertificateAuthorityTest extends CertificationDomainTestCase {
    private ProductionCertificateAuthority prodCa;
    private KeyPairEntity kp;
    private KeyPairService keyPairService;
    private CertificateRequestCreationService certificateRequestCreationService;
    private CertificateManagementService certificateManagementService;

    @Before
    public void setUp() {
        prodCa = createProductionCertificateAuthority(12, PRODUCTION_CA_NAME);
        keyPairService = mock(KeyPairService.class);
        kp = TestObjects.createActiveKeyPair("TEST-KEY");
        RepositoryConfiguration certificateConfiguration = mock(RepositoryConfiguration.class);
        when(certificateConfiguration.getPublicRepositoryUri()).thenReturn(BASE_URI);
        when(certificateConfiguration.getTrustAnchorRepositoryUri()).thenReturn(BASE_URI);
        certificateRequestCreationService = new CertificateRequestCreationServiceBean(certificateConfiguration);
        certificateManagementService = mock(CertificateManagementService.class);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldCreateKeyAndFirstRequestForNewCertifiableResourceClass() {
        when(keyPairService.createKeyPairEntity(isA(String.class))).thenReturn(kp);

        prodCa.processCertifiableResources(PRODUCTION_CA_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = prodCa.getUpStreamCARequestEntity().getUpStreamCARequest();

        List<TaRequest> taRequests = trustAnchorRequest.getTaRequests();
        assertEquals(1, taRequests.size());
        assertTrue(taRequests.get(0) instanceof SigningRequest);

        verify(keyPairService).createKeyPairEntity(isA(String.class));
    }

    @Test
    public void shouldNotCreateKeyAndFirstRequestForEmptyCertifiableResourceClass() {
        when(keyPairService.createKeyPairEntity(isA(String.class))).thenReturn(kp);

        prodCa.processCertifiableResources(IP_PRIVATE_USE_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = prodCa.getUpStreamCARequestEntity().getUpStreamCARequest();

        List<TaRequest> taRequests = trustAnchorRequest.getTaRequests();
        assertEquals(1, taRequests.size());
        assertTrue(taRequests.get(0) instanceof SigningRequest);

        verify(keyPairService).createKeyPairEntity(isA(String.class));
    }

    @Test
    public void shouldNotRequestIncomingCertificateForEmptyResourceSet() {
        prodCa.processCertifiableResources(new IpResourceSet(), keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = prodCa.getUpStreamCARequestEntity().getUpStreamCARequest();
        assertEquals(0, trustAnchorRequest.getTaRequests().size());
    }

    @Test
    public void shouldNotRequestIncomingCertificateIfTheCurrentOneIsSatisfactory() {
        prodCa.addKeyPair(kp);

        IncomingResourceCertificate currentCertificate = TestObjects.createResourceCertificate(
            123L,
            kp,
            new ValidityPeriod(new DateTime().minusYears(2), new DateTime().plusYears(5).plusMinutes(1)),
            PRODUCTION_CA_RESOURCES,
            createSia()
        );
        kp.updateIncomingResourceCertificate(currentCertificate.getCertificate(), currentCertificate.getPublicationUri());

        prodCa.processCertifiableResources(PRODUCTION_CA_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = prodCa.getUpStreamCARequestEntity().getUpStreamCARequest();
        assertEquals(0, trustAnchorRequest.getTaRequests().size());
    }

    @Test
    public void shouldRequestIncomingCertificateIfTheCurrentHasDifferentResources() {
        KeyPairEntity kp = TestObjects.createTestKeyPair("TEST-KEY");
        prodCa.addKeyPair(kp);

        IpResourceSet otherResources = IpResourceSet.parse("10/8");
        assertNotEquals(PRODUCTION_CA_RESOURCES, otherResources);

        IncomingResourceCertificate currentCertificate = TestObjects.createResourceCertificate(
            123L,
            kp,
            new ValidityPeriod(new DateTime().minusYears(2), new DateTime().plusYears(5).plusMinutes(1)),
            otherResources,
            createSia()
        );
        kp.updateIncomingResourceCertificate(currentCertificate.getCertificate(), currentCertificate.getPublicationUri());

        prodCa.processCertifiableResources(PRODUCTION_CA_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = prodCa.getUpStreamCARequestEntity().getUpStreamCARequest();

        List<TaRequest> taRequests = trustAnchorRequest.getTaRequests();
        assertEquals(1, taRequests.size());
        assertTrue(taRequests.get(0) instanceof SigningRequest);
    }

    @Test
    public void shouldRequestIncomingCertificateIfNotAfterTimeLessThenFiveYears() {
        KeyPairEntity kp = TestObjects.createTestKeyPair("TEST-KEY");
        prodCa.addKeyPair(kp);

        IncomingResourceCertificate currentCertificate = TestObjects.createResourceCertificate(
            123L,
            kp,
            new ValidityPeriod(new DateTime().minusYears(2), new DateTime().plusYears(5).minusSeconds(1)),
            PRODUCTION_CA_RESOURCES,
            createSia()
        );
        kp.updateIncomingResourceCertificate(currentCertificate.getCertificate(), currentCertificate.getPublicationUri());

        prodCa.processCertifiableResources(PRODUCTION_CA_RESOURCES, keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = prodCa.getUpStreamCARequestEntity().getUpStreamCARequest();

        List<TaRequest> taRequests = trustAnchorRequest.getTaRequests();
        assertEquals(1, taRequests.size());
        assertTrue(taRequests.get(0) instanceof SigningRequest);
    }

    @Test(expected = CertificateAuthorityException.class)
    public void shouldVerifyResourcesWhenIssuingEndEntityResourceCertificate() {

        KeyPairEntity kp = TestObjects.createTestKeyPair("TEST-KEY");
        prodCa.addKeyPair(kp);

        IncomingResourceCertificate currentCertificate = TestObjects.createResourceCertificate(
            123L,
            kp,
            new ValidityPeriod(new DateTime().minusYears(2), new DateTime().plusYears(5).plusMinutes(1)),
            IpResourceSet.parse("10/8"),
            createSia()
        );
        kp.updateIncomingResourceCertificate(currentCertificate.getCertificate(), currentCertificate.getPublicationUri());

        CertificateIssuanceRequest request = new CertificateIssuanceRequest(IpResourceSet.parse("11.0.0.0/8"), new X500Principal("CN=test"), kp.getPublicKey(), createSia());
        certificateManagementService.issueSingleUseEeResourceCertificate(prodCa, request, TEST_VALIDITY_PERIOD, prodCa.getCurrentKeyPair());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToRemoveSignedKeyPair() {
        KeyPairEntity kp = TestObjects.createTestKeyPair("TEST-KEY");
        kp.activate();
        prodCa.addKeyPair(kp);

        assertFalse(kp.isRemovable());
        prodCa.removeKeyPair(kp.getName());
    }

    private X509CertificateInformationAccessDescriptor[] createSia() {
        return new X509CertificateInformationAccessDescriptor[]{
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY,
                BASE_URI),
            new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST,
                BASE_URI.resolve(kp.getManifestFilename())),
        };
    }

}
