package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.error.RequestNotPerformedResponsePayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.revocation.CertificateRevocationKeyElement;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayload;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateRevocationCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedPublicKeyData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.joda.time.Instant;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.LockTimeoutException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.UUID;

import static net.ripe.rpki.domain.CertificationDomainTestCase.PRODUCTION_CA_NAME;
import static net.ripe.rpki.domain.CertificationDomainTestCase.PRODUCTION_CA_RESOURCES;
import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static net.ripe.rpki.domain.TestObjects.TEST_KEY_PAIR_2;
import static net.ripe.rpki.ripencc.provisioning.CertificateIssuanceProcessorTest.NON_HOSTED_CA_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.Silent.class)
public class ProvisioningRequestProcessorBeanTest {

    public static final PublicKey PUBLIC_KEY = TEST_KEY_PAIR_2.getPublicKey();
    private ProvisioningRequestProcessorBean subject;

    @Mock
    private CertificateAuthorityViewService certificateAuthorityViewService;
    @Mock
    private ProvisioningCmsResponseGenerator provisioningCmsResponseGenerator;
    @Mock
    private ProvisioningCmsSigningTimeStore provisioningCmsSigningTimeStore;
    @Mock
    private CommandService commandService;
    private ManagedCertificateAuthorityData parent;
    private NonHostedCertificateAuthorityData child;
    @Mock
    private ProvisioningCmsValidationStrategy validationStrategy;

    private ProvisioningCmsObject listCms;

    @Before
    public void setUp() {
        parent = new ManagedCertificateAuthorityData(
            new VersionedId(42L, 1), PRODUCTION_CA_NAME, UUID.randomUUID(), 1L,
            CertificateAuthorityType.ROOT,
            PRODUCTION_CA_RESOURCES,
            Collections.emptyList()
        );
        child = new NonHostedCertificateAuthorityData(
            new VersionedId(1234L, 1), NON_HOSTED_CA_NAME, UUID.randomUUID(), parent.getId(),
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT,
            new IpResourceSet(),
            Collections.singleton(new NonHostedPublicKeyData(PUBLIC_KEY, PayloadMessageType.issue, new RequestedResourceSets(), null))
        );

        subject = new ProvisioningRequestProcessorBean(
            certificateAuthorityViewService,
            validationStrategy,
            provisioningCmsSigningTimeStore,
            provisioningCmsResponseGenerator,
            null,
            null,
            new CertificateRevocationProcessor(null, commandService)
        );

        listCms = givenListResourceClassRequestCms();

        // set up mocks
        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(ProductionCertificateAuthority.class,
                UUID.fromString(listCms.getPayload().getRecipient()))).thenReturn(parent);
        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(NonHostedCertificateAuthority.class,
                UUID.fromString(listCms.getPayload().getSender()))).thenReturn(child);
    }

    @Test
    public void shouldEnsureNonHostedMemberIsChildOfDelegationCa() {
        parent = new ManagedCertificateAuthorityData(
            new VersionedId(99L, 1), PRODUCTION_CA_NAME, UUID.randomUUID(), 1L,
            CertificateAuthorityType.ROOT,
            PRODUCTION_CA_RESOURCES,
            Collections.emptyList()
        );
        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(ProductionCertificateAuthority.class,
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

        when(certificateAuthorityViewService.findCertificateAuthorityByTypeAndUuid(NonHostedCertificateAuthority.class, sender)).thenReturn(child);

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


    // https://datatracker.ietf.org/doc/html/rfc6492#section-3 -> 1101 error response
    @Test
    public void shouldCreateErrorResponseOnConcurrentRequest() {
        X509Certificate cmsCertificate = mock(X509Certificate.class);
        ArgumentCaptor<AbstractProvisioningResponsePayload> argumentCaptor = ArgumentCaptor.forClass(AbstractProvisioningResponsePayload.class);

        final String sender = listCms.getPayload().getSender(), recipient = listCms.getPayload().getRecipient();

        when(commandService.execute(any(ProvisioningCertificateRevocationCommand.class))).thenThrow(new LockTimeoutException());

        ProvisioningCmsObject revocationCms = getProvisioningCmsObject(sender, recipient, cmsCertificate, new CertificateRevocationRequestPayload(new CertificateRevocationKeyElement(DEFAULT_RESOURCE_CLASS, KeyPairUtil.getEncodedKeyIdentifier(PUBLIC_KEY))));

        subject.process(revocationCms);

        verify(provisioningCmsResponseGenerator).createProvisioningCmsResponseObject(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isInstanceOfSatisfying(RequestNotPerformedResponsePayload.class, (payload) -> {
            assertThat(payload.getStatus()).isEqualTo(NotPerformedError.ALREADY_PROCESSING_REQUEST);
        });
    }

    @Test
    public void shouldSetTheSenderAndRecipientIntoTheResponsePayload() {
        ArgumentCaptor<DateTime> timestampCaptor = ArgumentCaptor.forClass(DateTime.class);
        ArgumentCaptor<AbstractProvisioningResponsePayload> captor = ArgumentCaptor.forClass(AbstractProvisioningResponsePayload.class);

        subject.process(listCms);

        // payload was validated
        verify(validationStrategy).validateProvisioningCmsAndIdentityCertificate(eq(listCms), any(), any());

        // object is signed
        verify(provisioningCmsResponseGenerator).createProvisioningCmsResponseObject(captor.capture());
        AbstractProvisioningResponsePayload responsePayload = captor.getValue();

        // signing time on CA (mock) is copied from CMS
        verify(provisioningCmsSigningTimeStore).updateLastSeenProvisioningCmsSeenAt(same(child), timestampCaptor.capture());

        // Compare instants since these are not zoned.
        // note that while the sql Timestamp has nano-seconds (and Instant on JDK11+ as well), the (joda-time) signing
        // time of the CMS does not, thus the nanoseconds are 0.
        assertEquals(timestampCaptor.getValue().toInstant(), Instant.ofEpochMilli(listCms.getSigningTime().getMillis()));

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
        return getProvisioningCmsObject(sender, recipient, cmsCertificate, payload);
    }

    private ProvisioningCmsObject getProvisioningCmsObject(String sender, String recipient, X509Certificate cmsCertificate, AbstractProvisioningPayload payload) {
        payload.setSender(sender);
        payload.setRecipient(recipient);

        // Initialise with Joda DateTime, with millisecond resolution.
        return new ProvisioningCmsObject(null, cmsCertificate, null, null, payload, DateTime.now());
    }

}
