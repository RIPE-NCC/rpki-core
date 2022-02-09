package net.ripe.rpki.ripencc.provisioning;

import com.google.common.collect.Lists;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayload;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.domain.ResourceClassListQuery;
import net.ripe.rpki.domain.ResourceClassListResponse;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Optional;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ListResourceClassProcessorTest {

    private ProvisioningRequestProcessorBean processor;

    @Mock
    private NonHostedCertificateAuthority nonHostedCertificateAuthority;

    @Mock
    private ProductionCertificateAuthority productionCA;

    @Mock
    private ResourceLookupService resourceLookupService;

    private X509ResourceCertificate issuerCertificate;
    private URI uri;

    @Before
    public void setup() throws URISyntaxException {
        processor = new ProvisioningRequestProcessorBean(null, null, resourceLookupService, null, null);

        uri = new URI("rsync://test");
        issuerCertificate = mock(X509ResourceCertificate.class);

        IncomingResourceCertificate incomingResourceCertificate = mock(IncomingResourceCertificate.class);
        when(incomingResourceCertificate.getPublicationUri()).thenReturn(uri);
        when(incomingResourceCertificate.getCertificate()).thenReturn(issuerCertificate);

        when(productionCA.findCurrentIncomingResourceCertificate()).thenReturn(Optional.of(incomingResourceCertificate));

        X500Principal x500Principal = new X500Principal("CN=101");
        when(nonHostedCertificateAuthority.getName()).thenReturn(x500Principal);
        when(resourceLookupService.lookupMemberCaPotentialResources(x500Principal))
                .thenReturn(new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1")));

        IpResourceSet resources = new IpResourceSet();
        resources.add(Ipv4Address.parse("127.0.0.1"));
        resources.add(Ipv6Address.parse("::1"));
        ResourceClassListResponse resourceClassListResponse = new ResourceClassListResponse(resources);
        when(productionCA.processResourceClassListQuery(any(ResourceClassListQuery.class))).thenReturn(resourceClassListResponse);

    }

    @Test
    public void shouldBuildClassElementIntoTheResponsePayload() {
        ResourceClassListQueryPayload requestPayload = createPayload();

        ResourceClassListResponsePayload responsePayload = (ResourceClassListResponsePayload) processor.processRequestPayload(nonHostedCertificateAuthority, productionCA, requestPayload);

        ResourceClassListResponseClassElement resourceClassListResponseClassElement = responsePayload.getClassElements().get(0);

        assertEquals(DEFAULT_RESOURCE_CLASS, resourceClassListResponseClassElement.getClassName());
        assertSame(uri, resourceClassListResponseClassElement.getCertificateAuthorityUri().get(0));
        assertSame(issuerCertificate, resourceClassListResponseClassElement.getIssuer());
        assertTrue(new DateTime().isBefore(resourceClassListResponseClassElement.getValidityNotAfter()));
    }

    @Test
    public void shouldBuildClassElementWithCertificateElement() {
        RequestedResourceSets requestedResourceSets = new RequestedResourceSets(Optional.empty(), Optional.of(IpResourceSet.parse("127.0.0.0/8")), Optional.empty());
        IpResourceSet certifiedResources = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        X509ResourceCertificate certificate = mock(X509ResourceCertificate.class);

        PublicKeyEntity keyEntity = mock(PublicKeyEntity.class);
        when(keyEntity.getRequestedResourceSets()).thenReturn(requestedResourceSets);
        OutgoingResourceCertificate resourceCertificate = mock(OutgoingResourceCertificate.class);
        when(resourceCertificate.isCurrent()).thenReturn(true);
        when(resourceCertificate.getCertificate()).thenReturn(certificate);
        when(resourceCertificate.getPublicationUri()).thenReturn(URI.create("rsync://url.com/whatever"));

        OutgoingResourceCertificate notCurrentResourceCertificate = mock(OutgoingResourceCertificate.class);
        when(notCurrentResourceCertificate.isCurrent()).thenReturn(false);

        when(keyEntity.getOutgoingResourceCertificates()).thenReturn(Lists.newArrayList(resourceCertificate, notCurrentResourceCertificate));
        when(nonHostedCertificateAuthority.getPublicKeys()).thenReturn(Collections.singletonList(keyEntity));
        ResourceClassListQueryPayload requestPayload = createPayload();

        X500Principal x500Principal = new X500Principal("CN=101");
        when(nonHostedCertificateAuthority.getName()).thenReturn(x500Principal);
        when(resourceLookupService.lookupMemberCaPotentialResources(x500Principal)).thenReturn(certifiedResources);

        ResourceClassListResponse resourceClassListResponse = new ResourceClassListResponse(IpResourceSet.parse("127.0.0.1"));
        when(productionCA.processResourceClassListQuery(any(ResourceClassListQuery.class))).thenReturn(resourceClassListResponse);

        ResourceClassListResponsePayload responsePayload = (ResourceClassListResponsePayload) processor.processRequestPayload(nonHostedCertificateAuthority, productionCA, requestPayload);

        assertEquals(1, responsePayload.getClassElements().get(0).getCertificateElements().size());

        CertificateElement certificateElement = responsePayload.getClassElements().get(0).getCertificateElements().get(0);
        assertNotNull(certificateElement);
        assertEquals(URI.create("rsync://url.com/whatever"), certificateElement.getIssuerCertificatePublicationUris().get(0));
        assertNull(certificateElement.getAllocatedAsn());
        assertEquals(IpResourceSet.parse("127.0.0.0/8"), certificateElement.getAllocatedIpv4());
        assertNull(certificateElement.getAllocatedIpv6());
        assertSame(certificate, certificateElement.getCertificate());

        assertEquals("127.0.0.1", getClassElementByName(DEFAULT_RESOURCE_CLASS, responsePayload).getResourceSetIpv4().toString());
    }

    private ResourceClassListQueryPayload createPayload() {
        ResourceClassListQueryPayload requestPayload = new ResourceClassListQueryPayloadBuilder().build();
        requestPayload.setRecipient("A");
        requestPayload.setSender("B");
        return requestPayload;
    }

    private ResourceClassListResponseClassElement getClassElementByName(String name, ResourceClassListResponsePayload responsePayload) {
        return responsePayload.getClassElements().stream()
            .filter(classElement -> name.equals(classElement.getClassName()))
            .findFirst().orElse(null);
    }

}
