package net.ripe.rpki.domain;


import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationServiceBean;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static net.ripe.rpki.domain.CertificationDomainTestCase.ALL_RESOURCES_CA_NAME;
import static net.ripe.rpki.domain.CertificationDomainTestCase.BASE_URI;
import static net.ripe.rpki.domain.CertificationDomainTestCase.createAllResourcesCertificateAuthority;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class AllResourcesCertificateAuthorityTest  {
    private AllResourcesCertificateAuthority allResourcesCa;
    private KeyPairEntity kp;
    private KeyPairService keyPairService;
    private CertificateRequestCreationService certificateRequestCreationService;
    private RepositoryConfiguration repositoryConfiguration;

    @Before
    public void setUp() {
        allResourcesCa = createAllResourcesCertificateAuthority(13, ALL_RESOURCES_CA_NAME);
        keyPairService = mock(KeyPairService.class);
        kp = TestObjects.createActiveKeyPair("TEST-KEY");
        repositoryConfiguration = mock(RepositoryConfiguration.class);
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(BASE_URI);
        when(repositoryConfiguration.getTrustAnchorRepositoryUri()).thenReturn(BASE_URI);
        certificateRequestCreationService = new CertificateRequestCreationServiceBean(repositoryConfiguration);

        when(keyPairService.createKeyPairEntity()).thenReturn(kp);
    }

    @Test
    public void shouldCreateKeyAndFirstRequestForNewCertifiableResourceClass() {
        allResourcesCa.processCertifiableResources(keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = allResourcesCa.getUpStreamCARequestEntity().getUpStreamCARequest();

        List<TaRequest> taRequests = trustAnchorRequest.getTaRequests();
        assertEquals(1, taRequests.size());
        assertTrue(taRequests.get(0) instanceof SigningRequest);

        verify(keyPairService).createKeyPairEntity();
    }

    @Test
    public void shouldNotCreateKeyAndFirstRequestForEmptyCertifiableResourceClass() {
        allResourcesCa.processCertifiableResources(keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = allResourcesCa.getUpStreamCARequestEntity().getUpStreamCARequest();

        verify(keyPairService).createKeyPairEntity();

        List<TaRequest> taRequests = trustAnchorRequest.getTaRequests();
        assertEquals(1, taRequests.size());
        assertTrue(taRequests.get(0) instanceof SigningRequest);
    }

    @Test
    public void shouldNotRequestIncomingCertificateIfTheCurrentOneIsSatisfactory() {
        IncomingResourceCertificate currentCertificate = TestObjects.createResourceCertificate(
            123L,
            kp,
            new ValidityPeriod(new DateTime().minusYears(2), new DateTime().plusYears(5).plusMinutes(1)),
            Resources.ALL_RESOURCES,
            createSia()
        );
        kp.updateIncomingResourceCertificate(currentCertificate.getCertificate(), currentCertificate.getPublicationUri());

        allResourcesCa.processCertifiableResources(keyPairService, certificateRequestCreationService);
        TrustAnchorRequest trustAnchorRequest = allResourcesCa.getUpStreamCARequestEntity().getUpStreamCARequest();
        assertEquals(0, trustAnchorRequest.getTaRequests().size());
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
