package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestElement;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.revocation.response.CertificateRevocationResponsePayload;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestParser;
import net.ripe.rpki.domain.CertificationDomainTestCase;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.security.PublicKey;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static net.ripe.rpki.ripencc.provisioning.CertificateIssuanceProcessorTest.NON_HOSTED_CA_NAME;
import static net.ripe.rpki.services.impl.handlers.ChildParentCertificateUpdateSagaNonHostedTest.SIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CertificateRevocationProcessorTest {

    private NonHostedCertificateAuthority nonHostedCertificateAuthority;

    private ProductionCertificateAuthority productionCA;

    @Mock
    private ResourceLookupService resourceLookupService;

    @Mock
    private CommandService commandService;

    private URI caRepositoryUri;

    private ProvisioningRequestProcessorBean processor;
    @Mock
    private CertificateManagementService certificateManagementService;

    @Before
    public void setUp() {
        productionCA = CertificationDomainTestCase.createInitialisedProdCaWithRipeResources(certificateManagementService);
        nonHostedCertificateAuthority = new NonHostedCertificateAuthority(1234L, NON_HOSTED_CA_NAME, ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, productionCA);
        caRepositoryUri = URI.create("rsync://tmp/repo");
        processor = new ProvisioningRequestProcessorBean(null, null, resourceLookupService, commandService, null);
    }

    @Test
    public void shouldProcessCertificateRevocationRequest() throws Exception {
        PKCS10CertificationRequest certificate = TestObjects.getPkcs10CertificationRequest(caRepositoryUri);
        RpkiCaCertificateRequestParser parsedCertificate = new RpkiCaCertificateRequestParser(certificate);
        PublicKey publicKey = parsedCertificate.getPublicKey();
        PublicKeyEntity publicKeyEntity = nonHostedCertificateAuthority.findOrCreatePublicKeyEntityByPublicKey(publicKey);
        publicKeyEntity.setLatestIssuanceRequest(new CertificateIssuanceRequestElement(), SIA);

        String publicKeyHash = KeyPairUtil.getEncodedKeyIdentifier(publicKey);
        CertificateRevocationRequestPayload requestPayload = createPayload(publicKeyHash);

        CertificateRevocationResponsePayload response = (CertificateRevocationResponsePayload) processor.processRequestPayload(
            nonHostedCertificateAuthority, productionCA, requestPayload);

        verify(commandService, times(1)).execute(isA(UpdateAllIncomingResourceCertificatesCommand.class));
        assertNotNull(response);
        assertEquals(DEFAULT_RESOURCE_CLASS, response.getKeyElement().getClassName());
        assertEquals(publicKeyHash, response.getKeyElement().getPublicKeyHash());
        assertThat(publicKeyEntity.getLatestProvisioningRequestType()).isEqualTo(PayloadMessageType.revoke);
        assertThat(publicKeyEntity.findCurrentOutgoingResourceCertificate()).isEmpty();
    }

    public CertificateRevocationRequestPayload createPayload(String publicKeyHash) {
        CertificateRevocationRequestPayloadBuilder certificateRevocationRequestPayloadBuilder = new CertificateRevocationRequestPayloadBuilder();
        certificateRevocationRequestPayloadBuilder.withClassName(DEFAULT_RESOURCE_CLASS);
        certificateRevocationRequestPayloadBuilder.withPublicKeyHash(publicKeyHash);
        CertificateRevocationRequestPayload requestPayload = certificateRevocationRequestPayloadBuilder.build();
        requestPayload.setRecipient("A");
        requestPayload.setSender("B");
        return requestPayload;
    }

}
