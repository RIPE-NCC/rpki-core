package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.issue.request.CertificateIssuanceRequestPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.issue.response.CertificateIssuanceResponsePayload;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.provisioning.x509.pkcs10.RpkiCaCertificateRequestBuilder;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateIssuanceCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static net.ripe.rpki.domain.CertificationDomainTestCase.PRODUCTION_CA_NAME;
import static net.ripe.rpki.domain.CertificationDomainTestCase.PRODUCTION_CA_RESOURCES;
import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CertificateIssuanceProcessorTest {

    public static final X500Principal NON_HOSTED_CA_NAME = new X500Principal("CN=non-hosted");
    private NonHostedCertificateAuthorityData nonHostedCertificateAuthority;

    private HostedCertificateAuthorityData productionCA;

    @Mock
    private ResourceLookupService resourceLookupService;

    @Mock
    private ResourceCertificateViewService resourceCertificateViewService;

    @Mock
    private CommandService commandService;

    private URI caRepositoryUri;

    private CertificateIssuanceProcessor processor;

    @Before
    public void setUp() {
        caRepositoryUri = URI.create("rsync://tmp/repo/");

        productionCA = new HostedCertificateAuthorityData(
            new VersionedId(42L, 1), PRODUCTION_CA_NAME, UUID.randomUUID(), 1L,
            CertificateAuthorityType.ROOT,
            PRODUCTION_CA_RESOURCES,
            Collections.emptyList()
        );
        nonHostedCertificateAuthority = new NonHostedCertificateAuthorityData(
            new VersionedId(1234L, 1), NON_HOSTED_CA_NAME, UUID.randomUUID(), productionCA.getId(),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT,
            Instant.now(),
            new IpResourceSet(),
            Collections.emptySet()
        );

        processor = new CertificateIssuanceProcessor(resourceLookupService, commandService, resourceCertificateViewService);

        when(resourceLookupService.lookupMemberCaPotentialResources(NON_HOSTED_CA_NAME)).thenReturn(IpResourceSet.parse("10/8,fc00::/48"));

        ResourceCertificateData productionSigningCertificate = new ResourceCertificateData(mock(X509ResourceCertificate.class), caRepositoryUri.resolve("cert.cer"));
        when(resourceCertificateViewService.findCurrentIncomingResourceCertificate(productionCA.getId()))
            .thenReturn(Optional.of(productionSigningCertificate));
    }

    @Test
    public void shouldProcessCertificateIssuanceRequest() {
        X509ResourceCertificate mockCertificate = mock(X509ResourceCertificate.class);
        RequestedResourceSets requestedResources = new RequestedResourceSets(
            Optional.of(IpResourceSet.parse("AS3333")),
            Optional.of(IpResourceSet.parse("10/9")),
            Optional.empty()
        );
        CertificateIssuanceRequestPayload requestPayload = createPayload(caRepositoryUri, requestedResources);
        when(resourceCertificateViewService.findCurrentOutgoingResourceCertificate(eq(nonHostedCertificateAuthority.getId()), any()))
            .thenReturn(Optional.of(new ResourceCertificateData(mockCertificate, caRepositoryUri)));

        CertificateIssuanceResponsePayload response = processor.process(
            nonHostedCertificateAuthority, productionCA, requestPayload);

        verify(commandService).execute(isA(ProvisioningCertificateIssuanceCommand.class));

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
        assertThat(certificateElement.getCertificate()).isSameAs(mockCertificate);
        assertEquals(IpResourceSet.parse("AS3333"), certificateElement.getAllocatedAsn());
        assertEquals(IpResourceSet.parse("10/9"), certificateElement.getAllocatedIpv4());
        assertNull(certificateElement.getAllocatedIpv6());
    }

    @Test
    public void shouldFailToProcessAnyResourceClassExceptDefault() {
        CertificateIssuanceRequestPayload requestPayload = createPayloadClass("RIPE", caRepositoryUri, new RequestedResourceSets(Optional.empty(), Optional.of(IpResourceSet.parse("11/8")), Optional.empty()));
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_NO_SUCH_RESOURCE_CLASS);
            });
    }

    //http://tools.ietf.org/html/rfc6492#page-23
    @Test
    public void shouldReturnNoResourcesAllocatedResponsePayloadIfClientHoldsResources() {
        when(resourceLookupService.lookupMemberCaPotentialResources(NON_HOSTED_CA_NAME)).thenReturn(new IpResourceSet());

        CertificateIssuanceRequestPayload requestPayload = createPayload(caRepositoryUri);
        assertThatThrownBy(()-> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_NO_RESOURCES_ALLOTED_IN_RESOURCE_CLASS);
            });
    }


    //http://tools.ietf.org/html/rfc6492#page-23
    @Test
    public void shouldReturnReqBadlyFormedCertificateRequestIfClientSendsAnInvalidCertificate() {
        PKCS10CertificationRequest invalidCertificate = mock(PKCS10CertificationRequest.class);
        final CertificationRequest certificationRequest = mock(CertificationRequest.class);
        when(invalidCertificate.toASN1Structure()).thenReturn(certificationRequest);

        CertificateIssuanceRequestPayload requestPayload = createPayload(invalidCertificate);

        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
            });
    }

    @Test
    public void should_check_requested_resource_set_entries_limit() {
        IpResourceSet tooBig = new IpResourceSet();
        for (int i = 0; i <= CertificateIssuanceProcessor.RESOURCE_SET_ENTRIES_LIMIT; ++i) {
            tooBig.add(new Asn(2 * i));
        }

        RequestedResourceSets requestedResources = new RequestedResourceSets(
            Optional.of(tooBig),
            Optional.of(IpResourceSet.parse("10/9")),
            Optional.empty()
        );
        CertificateIssuanceRequestPayload requestPayload = createPayload(caRepositoryUri, requestedResources);
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(error.getMessage()).isEqualTo("requested resource set exceeds entry limit (100001 > 100000)");
            });
    }

    @Test
    public void should_check_sia_uri_scheme() {
        CertificateIssuanceRequestPayload requestPayload = createPayload(URI.create("foo://bar"));
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(error.getMessage()).isEqualTo("SIA URI scheme is not correct");
            });
    }

    @Test
    public void should_check_sia_uri_is_normalized() {
        CertificateIssuanceRequestPayload requestPayload = createPayload(URI.create("rsync://rpki.example.com/repository/../"));
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(error.getMessage()).isEqualTo("SIA URI is not normalized");
            });
    }

    @Test
    public void should_check_sia_uri_is_absolute() {
        CertificateIssuanceRequestPayload requestPayload = createPayload(URI.create("//rpki.example.com/repository/"));
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(error.getMessage()).isEqualTo("SIA URI is not absolute or is opaque");
            });
    }

    @Test
    public void should_check_sia_uri_is_not_opaque() {
        CertificateIssuanceRequestPayload requestPayload = createPayload(URI.create("rsync:repository/"));
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(error.getMessage()).isEqualTo("SIA URI is not absolute or is opaque");
            });
    }

    @Test
    public void should_check_sia_uri_does_not_try_to_escape_the_repository_directory() {
        // Note that URIs with intermediate `..` segments are already handled by the `normalize` check.
        CertificateIssuanceRequestPayload requestPayload = createPayload(URI.create("rsync://rpki.example.com/../trying/to/escape/the/repository/"));
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(error.getMessage()).isEqualTo("SIA URI contains relative segments");
            });
    }

    @Test
    public void should_check_sia_uri_is_not_too_long() {
        StringBuilder longString = new StringBuilder("rsync://rpki.example.com/");
        for (int i = 0; i < 210; ++i) {
            longString.append("directory/");
        }

        CertificateIssuanceRequestPayload requestPayload = createPayload(URI.create(longString.toString()));
        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (response) -> {
                assertThat(response.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(response.getMessage()).isEqualTo("maximum SIA URI length exceeded (2125 > 2048)");
            });
    }

    @Test
    public void should_check_public_key_length() throws Exception {
        RSAKeyGenParameterSpec params = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
        CertificateIssuanceRequestPayload requestPayload = createPayload(params);

        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (response) -> {
                assertThat(response.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(response.getMessage()).isEqualTo("public key size is not 2048");
            });
    }

    @Test
    public void should_check_public_key_public_exponent() throws Exception {
        RSAKeyGenParameterSpec params = new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F0);
        CertificateIssuanceRequestPayload requestPayload = createPayload(params);

        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, productionCA, requestPayload))
            .isInstanceOfSatisfying(NotPerformedException.class, (response) -> {
                assertThat(response.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_BADLY_FORMED_CERTIFICATE_REQUEST);
                assertThat(response.getMessage()).isEqualTo("public key exponent is not 65537");
            });
    }

    private CertificateIssuanceRequestPayload createPayload(RSAKeyGenParameterSpec params) throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "SunRsaSign");
        generator.initialize(params);
        KeyPair keyPair = generator.generateKeyPair();

        PKCS10CertificationRequest request = new RpkiCaCertificateRequestBuilder()
            .withSubject(new X500Principal("CN=NON-HOSTED"))
            .withCaRepositoryUri(caRepositoryUri)
            .withManifestUri(URI.create("rsync://tmp/manifest"))
            .build(keyPair);

        return createPayload(request);
    }

    private CertificateIssuanceRequestPayload createPayload(URI caRepositoryUri) {
        return createPayload(caRepositoryUri, new RequestedResourceSets());
    }

    private CertificateIssuanceRequestPayload createPayload(PKCS10CertificationRequest certificationRequest) {
        return createPayload(certificationRequest, new RequestedResourceSets());
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
