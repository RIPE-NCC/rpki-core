package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponsePayload;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.ncc.core.services.activation.CertificateManagementService;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CertificateIssuanceProcessorTest {

    public static final X500Principal NON_HOSTED_CA_NAME = new X500Principal("CN=non-hosted");
    private NonHostedCertificateAuthority nonHostedCertificateAuthority;

    private ProductionCertificateAuthority productionCA;

    @Mock
    private ResourceLookupService resourceLookupService;

    @Mock
    private CertificateManagementService certificateManagementService;

    @Mock
    private CommandService commandService;

    private URI caRepositoryUri;

    private ProvisioningRequestProcessorBean processor;
    private IpResourceSet certifiableResources;

    @Before
    public void setUp() {
        productionCA = CertificationDomainTestCase.createInitialisedProdCaWithRipeResources(certificateManagementService);
        nonHostedCertificateAuthority = new NonHostedCertificateAuthority(1234L, NON_HOSTED_CA_NAME, ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, productionCA);
        caRepositoryUri = URI.create("rsync://tmp/repo");

        certifiableResources = IpResourceSet.parse("10/8,fc00::/48");
        when(resourceLookupService.lookupMemberCaPotentialResources(NON_HOSTED_CA_NAME)).thenReturn(certifiableResources);

        processor = new ProvisioningRequestProcessorBean(null, null, resourceLookupService, commandService, null);
    }

    @Test
    public void shouldProcessCertificateIssuanceRequest() {
        RequestedResourceSets requestedResources = new RequestedResourceSets(
            Optional.of(IpResourceSet.parse("AS3333")),
            Optional.of(IpResourceSet.parse("10/9")),
            Optional.empty()
        );
        CertificateIssuanceRequestPayload requestPayload = createPayload(caRepositoryUri, requestedResources);
        when(commandService.execute(isA(UpdateAllIncomingResourceCertificatesCommand.class))).thenAnswer(invocation -> {
            PublicKeyEntity publicKey = nonHostedCertificateAuthority.getPublicKeys().iterator().next();
            DateTime now = DateTime.now(DateTimeZone.UTC);
            OutgoingResourceCertificate cert = TestObjects.createOutgoingResourceCertificate(
                44L,
                productionCA.getCurrentKeyPair(),
                publicKey.getPublicKey(),
                new ValidityPeriod(now, CertificateAuthority.calculateValidityNotAfter(now)),
                publicKey.getRequestedResourceSets().calculateEffectiveResources(certifiableResources),
                publicKey.getRequestedSia().toArray(new X509CertificateInformationAccessDescriptor[0])
            );
            publicKey.addOutgoingResourceCertificate(cert);
            return new CommandStatus();
        });
        AbstractProvisioningResponsePayload responsePayload = processor.processRequestPayload(
            nonHostedCertificateAuthority, productionCA, requestPayload);

        assertThat(responsePayload).isInstanceOf(CertificateIssuanceResponsePayload.class);
        CertificateIssuanceResponsePayload response = (CertificateIssuanceResponsePayload) responsePayload;

        assertThat(nonHostedCertificateAuthority.getPublicKeys()).hasSize(1).allSatisfy(pk -> {
            assertThat(pk.getLatestProvisioningRequestType()).isEqualTo(PayloadMessageType.issue);
            assertThat(pk.getRequestedResourceSets()).isEqualTo(requestedResources);
            assertThat(pk.getRequestedSia()).isEqualTo(Arrays.asList(
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, URI.create("rsync://tmp/repo")),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, URI.create("rsync://tmp/manifest"))
            ));
        });

        CertificateIssuanceResponseClassElement classElement = response.getClassElement();

        assertEquals(requestPayload.getRequestElement().getClassName(), classElement.getClassName());
        //assertEquals(caRepositoryUri, classElement.getCertificateAuthorityUri().get(0));
        assertTrue(new DateTime().isBefore(classElement.getValidityNotAfter()));

        // Certifiable resources
        assertEquals(IpResourceSet.parse(""), classElement.getResourceSetAsn());
        assertEquals(IpResourceSet.parse("10/8"), classElement.getResourceSetIpv4());
        assertEquals(IpResourceSet.parse("fc00::/48"), classElement.getResourceSetIpv6());

        // Requested resources
        CertificateElement certificateElement = classElement.getCertificateElement();
        assertNotNull(certificateElement.getCertificate());
        assertEquals(IpResourceSet.parse("AS3333"), certificateElement.getAllocatedAsn());
        assertEquals(IpResourceSet.parse("10/9"), certificateElement.getAllocatedIpv4());
        assertNull(certificateElement.getAllocatedIpv6());
        assertEquals(IpResourceSet.parse("10/9,fc00::/48"), certificateElement.getCertificate().getResources());
    }

    @Test
    public void shouldFailToProcessAnyResourceClassExceptDefault() {
        CertificateIssuanceRequestPayload requestPayload = createPayloadClass("RIPE", caRepositoryUri, new RequestedResourceSets(Optional.empty(), Optional.of(IpResourceSet.parse("11/8")), Optional.empty()));
        RequestNotPerformedResponsePayload response = (RequestNotPerformedResponsePayload) processor.processRequestPayload(nonHostedCertificateAuthority, productionCA, requestPayload);
        assertEquals(NotPerformedError.REQ_NO_SUCH_RESOURCE_CLASS, response.getStatus());
    }

    //http://tools.ietf.org/html/rfc6492#page-23
    @Test
    public void shouldReturnNoResourcesAllocatedResponsePayloadIfClientHoldsResources() {
        when(resourceLookupService.lookupMemberCaPotentialResources(NON_HOSTED_CA_NAME)).thenReturn(new IpResourceSet());

        CertificateIssuanceRequestPayload requestPayload = createPayload(caRepositoryUri);
        AbstractProvisioningResponsePayload response = processor.processRequestPayload(nonHostedCertificateAuthority, productionCA, requestPayload);

        assertEquals(PayloadMessageType.error_response, response.getType());
        assertEquals(NotPerformedError.REQ_NO_RESOURCES_ALLOTED_IN_RESOURCE_CLASS, ((RequestNotPerformedResponsePayload) response).getStatus());
    }


    //http://tools.ietf.org/html/rfc6492#page-23
    @Test
    public void shouldReturnReqBadlyFormedCertificateRequestIfClientSendsAnInvalidCertificate() {
        PKCS10CertificationRequest invalidCertificate = mock(PKCS10CertificationRequest.class);
        final CertificationRequest certificationRequest = mock(CertificationRequest.class);
        when(invalidCertificate.toASN1Structure()).thenReturn(certificationRequest);

        CertificateIssuanceRequestPayload requestPayload = createPayload(invalidCertificate);

        AbstractProvisioningResponsePayload response = processor.processRequestPayload(nonHostedCertificateAuthority, productionCA, requestPayload);

        assertEquals(PayloadMessageType.error_response, response.getType());
        assertEquals(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST, ((RequestNotPerformedResponsePayload) response).getStatus());
    }

    private CertificateIssuanceRequestPayload createPayload(URI caRepositoryUri) {
        return createPayload(caRepositoryUri, new RequestedResourceSets());
    }

    private CertificateIssuanceRequestPayload createPayload(PKCS10CertificationRequest invalidCertificate) {
        return createPayload(invalidCertificate, new RequestedResourceSets());
    }

    public CertificateIssuanceRequestPayload createPayload(URI caRepositoryUri, RequestedResourceSets resources) {
        PKCS10CertificationRequest certificate = TestObjects.getPkcs10CertificationRequest(caRepositoryUri);
        return createPayload(certificate, resources);
    }

    public CertificateIssuanceRequestPayload createPayload(PKCS10CertificationRequest certificate, RequestedResourceSets resources) {
        return createPayloadClass(DEFAULT_RESOURCE_CLASS, certificate, resources);
    }

    public CertificateIssuanceRequestPayload createPayloadClass(String className, URI caRepositoryUri, RequestedResourceSets resources) {
        PKCS10CertificationRequest certificate = TestObjects.getPkcs10CertificationRequest(caRepositoryUri);
        return createPayloadClass(className, certificate, resources);
    }

    public CertificateIssuanceRequestPayload createPayloadClass(String className, PKCS10CertificationRequest certificate, RequestedResourceSets resources) {
        CertificateIssuanceRequestPayloadBuilder certificateIssuanceRequestPayloadBuilder = new CertificateIssuanceRequestPayloadBuilder();
        certificateIssuanceRequestPayloadBuilder.withClassName(className);
        certificateIssuanceRequestPayloadBuilder.withCertificateRequest(certificate);
        certificateIssuanceRequestPayloadBuilder.withAllocatedAsn(resources.getRequestedResourceSetAsn().orElse(null));
        certificateIssuanceRequestPayloadBuilder.withIpv4ResourceSet(resources.getRequestedResourceSetIpv4().orElse(null));
        certificateIssuanceRequestPayloadBuilder.withIpv6ResourceSet(resources.getRequestedResourceSetIpv6().orElse(null));

        CertificateIssuanceRequestPayload requestPayload = certificateIssuanceRequestPayloadBuilder.build();

        requestPayload.setRecipient("A");
        requestPayload.setSender("B");
        return requestPayload;
    }

}
