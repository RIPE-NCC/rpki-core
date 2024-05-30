package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.payload.common.CertificateElement;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponseClassElement;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayload;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedPublicKeyData;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Optional;

import static net.ripe.rpki.domain.Resources.ALL_RESOURCES;
import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ListResourceClassProcessorTest {

    private ListResourceClassProcessor processor;

    @Mock
    private NonHostedCertificateAuthorityData nonHostedCertificateAuthority;

    @Mock
    private ResourceLookupService resourceLookupService;

    @Mock
    private ResourceCertificateViewService resourceCertificateViewService;

    private X509ResourceCertificate issuerCertificate;
    private URI uri;

    @Before
    public void setup() throws URISyntaxException, ResourceInformationNotAvailableException {
        processor = new ListResourceClassProcessor(resourceLookupService, resourceCertificateViewService);

        uri = new URI("rsync://test");
        issuerCertificate = mock(X509ResourceCertificate.class);
        when(issuerCertificate.resources()).thenReturn(ALL_RESOURCES);

        ResourceCertificateData incomingResourceCertificate = new ResourceCertificateData(issuerCertificate, uri);

        when(resourceCertificateViewService.findCurrentIncomingResourceCertificate(nonHostedCertificateAuthority.getParentId()))
            .thenReturn(Optional.of(incomingResourceCertificate));

        X500Principal x500Principal = new X500Principal("CN=101");
        when(nonHostedCertificateAuthority.getName()).thenReturn(x500Principal);
        when(resourceLookupService.lookupMemberCaPotentialResources(x500Principal))
                .thenReturn(Optional.of(ResourceExtension.ofResources(ImmutableResourceSet.parse("127.0.0.1,::1"))));
    }

    @Test
    public void shouldBuildClassElementIntoTheResponsePayload() throws ResourceInformationNotAvailableException {
        ResourceClassListResponsePayload responsePayload = processor.process(nonHostedCertificateAuthority);

        assertThat(responsePayload.getClassElements()).isNotEmpty();
        ResourceClassListResponseClassElement resourceClassListResponseClassElement = responsePayload.getClassElements().get(0);

        assertEquals(DEFAULT_RESOURCE_CLASS, resourceClassListResponseClassElement.getClassName());
        assertSame(uri, resourceClassListResponseClassElement.getCertificateAuthorityUri().get(0));
        assertSame(issuerCertificate, resourceClassListResponseClassElement.getIssuer());
        assertTrue(new DateTime(DateTimeZone.UTC).isBefore(resourceClassListResponseClassElement.getValidityNotAfter()));
    }

    @Test
    public void shouldBuildClassElementWithCertificateElement() throws ResourceInformationNotAvailableException {
        RequestedResourceSets requestedResourceSets = new RequestedResourceSets(Optional.empty(), Optional.of(ImmutableResourceSet.parse("127.0.0.0/8")), Optional.empty());
        ImmutableResourceSet certifiedResources = ImmutableResourceSet.parse("127.0.0.1,::1");
        X509ResourceCertificate certificate = mock(X509ResourceCertificate.class);

        ResourceCertificateData resourceCertificateData = new ResourceCertificateData(certificate, URI.create("rsync://url.com/whatever"));
        NonHostedPublicKeyData publicKeyData = new NonHostedPublicKeyData(mock(PublicKey.class), PayloadMessageType.issue, requestedResourceSets, resourceCertificateData);

        when(nonHostedCertificateAuthority.getPublicKeys()).thenReturn(Collections.singleton(publicKeyData));

        when(resourceLookupService.lookupMemberCaPotentialResources(nonHostedCertificateAuthority.getName())).thenReturn(Optional.of(ResourceExtension.ofResources(certifiedResources)));

        ResourceClassListResponsePayload responsePayload = processor.process(nonHostedCertificateAuthority);

        assertThat(responsePayload.getClassElements()).isNotEmpty();
        assertThat(responsePayload.getClassElements().get(0).getCertificateElements()).hasSize(1);

        CertificateElement certificateElement = responsePayload.getClassElements().get(0).getCertificateElements().get(0);
        assertNotNull(certificateElement);
        assertEquals(URI.create("rsync://url.com/whatever"), certificateElement.getIssuerCertificatePublicationUris().get(0));
        assertNull(certificateElement.getAllocatedAsn());
        assertEquals(IpResourceSet.parse("127.0.0.0/8"), certificateElement.getAllocatedIpv4());
        assertNull(certificateElement.getAllocatedIpv6());
        assertSame(certificate, certificateElement.getCertificate());

        assertEquals("127.0.0.1", getDefaultResourceClassElement(responsePayload).getResourceSetIpv4().toString());
    }

    private ResourceClassListResponseClassElement getDefaultResourceClassElement(ResourceClassListResponsePayload responsePayload) {
        return responsePayload.getClassElements().stream()
            .filter(classElement -> DEFAULT_RESOURCE_CLASS.equals(classElement.getClassName()))
            .findFirst().orElse(null);
    }

}
