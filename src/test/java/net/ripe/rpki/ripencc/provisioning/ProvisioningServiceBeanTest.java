package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.provisioning.ProvisioningObjectMother;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObjectBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.list.response.ResourceClassListResponsePayloadBuilder;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningCmsCertificateBuilderTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class ProvisioningServiceBeanTest {

    private ProvisioningServiceBean subject;

    @Mock
    private ProvisioningRequestProcessor provisioningRequestProcessor;

    @Mock
    private ProvisioningAuditLogService provisioningAuditLogService;

    @Mock
    private ProvisioningMetricsService provisioningMetricsService;

    private ProvisioningCmsObject listCms;

    @Before
    public void setUp() {
        subject = new ProvisioningServiceBean(provisioningRequestProcessor, provisioningAuditLogService, provisioningMetricsService);
        listCms = givenListResourceClassRequestCms();
    }

    @Test
    public void shouldExtractRequestObject() throws ProvisioningException {
        ProvisioningCmsObject extractedListCms = subject.extractRequestObject(listCms.getEncoded());

        assertEquals(listCms, extractedListCms);
    }

    /**
     * http://tools.ietf.org/html/draft-ietf-sidr-rescerts-provisioning-10#section-3.2
     *
     * Should return 400 http (servlet will do this in response to exception) when CMS or XML are not well formed.
     */
    @Test(expected=ProvisioningException.class)
    public void shouldCreateErrorResponseForUnparseableRequest() {
        // Note this will cause a typed runtime exception that is caught and translated to the proper http response by the servlet
        subject.processRequest(new byte[]{0x4B, 0x61, 0x70, 0x6F, 0x74});

        // And we track the underlying a verification check causing the error
        verify(provisioningMetricsService).trackValidationResult(any());
    }

    @Test
    public void shouldLogRequestAndResponseInAuditTable() throws ProvisioningException {
        ProvisioningCmsObject response = mock(ProvisioningCmsObject.class);
        when(response.getPayload()).thenReturn(new ResourceClassListResponsePayloadBuilder().build());

        when(provisioningRequestProcessor.process(any())).thenReturn(response);
        subject.processRequest(listCms.getEncoded());

        // Verify that the response and reqeuest are logged
        verify(provisioningAuditLogService, times(2)).log(ArgumentMatchers.any(), ArgumentMatchers.any());
        // And both the request and response are counted once
        verify(provisioningMetricsService).trackPayload(any(ResourceClassListQueryPayload.class));
        verify(provisioningMetricsService).trackPayload(any(ResourceClassListResponsePayload.class));
    }

    private ProvisioningCmsObject givenListResourceClassRequestCms() {
        String sender = UUID.randomUUID().toString();
        String recipient = UUID.randomUUID().toString();
        return givenListResourceClassRequestCms(sender, recipient);
    }

    private ProvisioningCmsObject givenListResourceClassRequestCms(String sender, String recipient) {
        ResourceClassListQueryPayloadBuilder resourceClassListQueryPayloadBuilder = new ResourceClassListQueryPayloadBuilder();
        ResourceClassListQueryPayload payload = resourceClassListQueryPayloadBuilder.build();
        payload.setSender(sender);
        payload.setRecipient(recipient);
        ProvisioningCmsObjectBuilder builder = new ProvisioningCmsObjectBuilder();

        builder.withCmsCertificate(ProvisioningCmsCertificateBuilderTest.TEST_CMS_CERT.getCertificate()).withCrl(ProvisioningObjectMother.CRL).withPayloadContent(payload);

        return builder.build(ProvisioningCmsCertificateBuilderTest.EE_KEYPAIR.getPrivate());
    }

}
