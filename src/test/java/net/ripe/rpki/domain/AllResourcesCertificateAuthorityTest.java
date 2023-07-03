package net.ripe.rpki.domain;


import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationServiceBean;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static net.ripe.rpki.domain.TestObjects.ALL_RESOURCES_CA_NAME;
import static net.ripe.rpki.domain.TestObjects.BASE_URI;
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
        allResourcesCa = new AllResourcesCertificateAuthority(13, ALL_RESOURCES_CA_NAME, UUID.randomUUID());
        keyPairService = mock(KeyPairService.class);
        kp = TestObjects.createActiveKeyPair("TEST-KEY");
        repositoryConfiguration = mock(RepositoryConfiguration.class);
        when(repositoryConfiguration.getPublicRepositoryUri()).thenReturn(BASE_URI);
        when(repositoryConfiguration.getTrustAnchorRepositoryUri()).thenReturn(BASE_URI);
        certificateRequestCreationService = new CertificateRequestCreationServiceBean(repositoryConfiguration, keyPairService);

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
}
