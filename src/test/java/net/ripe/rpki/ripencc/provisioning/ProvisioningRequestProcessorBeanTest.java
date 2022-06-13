package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.UUID;

import static net.ripe.rpki.domain.CertificationDomainTestCase.PRODUCTION_CA_NAME;
import static net.ripe.rpki.domain.CertificationDomainTestCase.PRODUCTION_CA_RESOURCES;
import static net.ripe.rpki.ripencc.provisioning.CertificateIssuanceProcessorTest.NON_HOSTED_CA_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.Silent.class)
public class ProvisioningRequestProcessorBeanTest {

    private ProvisioningRequestProcessorBean subject;

    @Mock
    private CertificateAuthorityViewService certificateAuthorityViewService;
    @Mock
    private ProvisioningCmsResponseGenerator provisioningCmsResponseGenerator;
    @Mock
    private CommandService commandService;
    private HostedCertificateAuthorityData parent;
    private NonHostedCertificateAuthorityData child;
    @Mock
    private ProvisioningCmsValidationStrategy validationStrategy;

    private ProvisioningCmsObject listCms;

    @Before
    public void setUp() {
        parent = new HostedCertificateAuthorityData(
            new VersionedId(42L, 1), PRODUCTION_CA_NAME, UUID.randomUUID(), 1L,
            CertificateAuthorityType.ROOT,
            PRODUCTION_CA_RESOURCES,
            Collections.emptyList()
        );
        child = new NonHostedCertificateAuthorityData(
            new VersionedId(1234L, 1), NON_HOSTED_CA_NAME, UUID.randomUUID(), parent.getId(),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT,
            Instant.now(),
            new IpResourceSet(),
            Collections.emptySet()
        );

        subject = new ProvisioningRequestProcessorBean(
            certificateAuthorityViewService,
            validationStrategy, provisioningCmsResponseGenerator,
            null,
            null,
            new CertificateRevocationProcessor(null, commandService)
        );

        listCms = givenListResourceClassRequestCms();

        // set up mocks
        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(CertificateAuthorityType.ROOT,
                UUID.fromString(listCms.getPayload().getRecipient()))).thenReturn(parent);
        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(CertificateAuthorityType.NONHOSTED,
                UUID.fromString(listCms.getPayload().getSender()))).thenReturn(child);
    }

    @Test
    public void shouldEnsureNonHostedMemberIsChildOfDelegationCa() {
        parent = new HostedCertificateAuthorityData(
            new VersionedId(99L, 1), PRODUCTION_CA_NAME, UUID.randomUUID(), 1L,
            CertificateAuthorityType.ROOT,
            PRODUCTION_CA_RESOURCES,
            Collections.emptyList()
        );
        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(CertificateAuthorityType.ROOT,
            UUID.fromString(listCms.getPayload().getRecipient()))).thenReturn(parent);

        try {
            subject.process(listCms);
            fail("ProvisioningException expected");
        } catch (ProvisioningException expected) {
            assertEquals(ResponseExceptionType.BAD_SENDER_AND_RECIPIENT, expected.getResponseExceptionType());
        }
    }

    @Test
    public void shouldCreateErrorResponseForUnparsableSenderUUID() {
        listCms = givenListResourceClassRequestCms("^&*--not-an-uuid", UUID.randomUUID().toString());

        try {
            subject.process(listCms);
            fail("ProvisioningException expected");
        } catch (ProvisioningException expected) {
            assertEquals(ResponseExceptionType.BAD_SENDER_AND_RECIPIENT, expected.getResponseExceptionType());
        }
    }

    @Test
    public void shouldCreateErrorResponseForUnparsableRecipientUUID() {
        listCms = givenListResourceClassRequestCms(UUID.randomUUID().toString(), "^&*--not-an-uuid");

        try {
            subject.process(listCms);
            fail("ProvisioningException expected");
        } catch (ProvisioningException expected) {
            assertEquals(ResponseExceptionType.BAD_SENDER_AND_RECIPIENT, expected.getResponseExceptionType());
        }
    }

    @Test
    public void shouldCreateErrorResponseWhenRequestingWithoutOwnership() throws Exception {
        X509Certificate cmsCertificate = mock(X509Certificate.class);
        doThrow(new InvalidKeyException()).when(cmsCertificate).verify(any(PublicKey.class));

        final UUID sender = UUID.randomUUID(), recipient = UUID.randomUUID();

        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(CertificateAuthorityType.NONHOSTED, sender)).thenReturn(child);

        listCms = givenListResourceClassRequestCms(sender.toString(), recipient.toString(), cmsCertificate);

        try {
            subject.process(listCms);
            fail("ProvisioningException expected");
        } catch (ProvisioningException expected) {
            assertEquals(ResponseExceptionType.UNKNOWN_RECIPIENT, expected.getResponseExceptionType());
        }
    }

    @Test
    public void shouldCreateErrorResponseWhenCertificateAuthorityIsMissing() throws Exception {
        X509Certificate cmsCertificate = mock(X509Certificate.class);
        doThrow(new InvalidKeyException()).when(cmsCertificate).verify(any(PublicKey.class));

        final UUID sender = UUID.randomUUID(), recipient = UUID.randomUUID();

        listCms = givenListResourceClassRequestCms(sender.toString(), recipient.toString(), cmsCertificate);

        try {
            subject.process(listCms);
            fail("ProvisioningException expected");
        } catch (ProvisioningException expected) {
            assertEquals(ResponseExceptionType.UNKNOWN_SENDER, expected.getResponseExceptionType());
        }
    }

    @Test
    public void shouldSetTheSenderAndRecipientIntoTheResponsePayload() {
        ArgumentCaptor<AbstractProvisioningResponsePayload> captor = ArgumentCaptor.forClass(AbstractProvisioningResponsePayload.class);

        subject.process(listCms);

        // payload was validated
        verify(validationStrategy).validateProvisioningCmsAndIdentityCertificate(eq(listCms), any());

        // object is signed
        verify(provisioningCmsResponseGenerator).createProvisioningCmsResponseObject(captor.capture());
        AbstractProvisioningResponsePayload responsePayload = captor.getValue();

        // and sender/receiver are correct
        assertEquals(listCms.getPayload().getSender(), responsePayload.getRecipient());
        assertEquals(listCms.getPayload().getRecipient(), responsePayload.getSender());
    }

    private ProvisioningCmsObject givenListResourceClassRequestCms() {
        String sender = UUID.randomUUID().toString();
        String recipient = UUID.randomUUID().toString();
        return givenListResourceClassRequestCms(sender, recipient);
    }

    private ProvisioningCmsObject givenListResourceClassRequestCms(String sender, String recipient) {
        X509Certificate cmsCertificate = mock(X509Certificate.class);
        return givenListResourceClassRequestCms(sender, recipient, cmsCertificate);
    }

    private ProvisioningCmsObject givenListResourceClassRequestCms(String sender, String recipient, X509Certificate cmsCertificate) {
        ResourceClassListQueryPayload payload = new ResourceClassListQueryPayloadBuilder().build();
        payload.setSender(sender);
        payload.setRecipient(recipient);

        return new ProvisioningCmsObject(null, cmsCertificate, null, null, payload);
    }

}
