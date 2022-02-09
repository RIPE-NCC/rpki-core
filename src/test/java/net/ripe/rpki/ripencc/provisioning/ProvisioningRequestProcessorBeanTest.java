package net.ripe.rpki.ripencc.provisioning;

import net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest;
import net.ripe.rpki.commons.crypto.util.PregeneratedKeyPairFactory;
import net.ripe.rpki.commons.provisioning.cms.ProvisioningCmsObject;
import net.ripe.rpki.commons.provisioning.payload.AbstractProvisioningPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayload;
import net.ripe.rpki.commons.provisioning.payload.list.request.ResourceClassListQueryPayloadBuilder;
import net.ripe.rpki.commons.provisioning.protocol.ResponseExceptionType;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.LockModeType;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.Silent.class)
public class ProvisioningRequestProcessorBeanTest {

    private ProvisioningRequestProcessorBean subject;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private CommandService commandService;
    @Mock
    private ProductionCertificateAuthority parent;
    @Mock
    private NonHostedCertificateAuthority child;
    @Mock
    private ProvisioningCmsValidationStrategy validationStrategy;

    private ProvisioningCmsObject listCms;

    private PregeneratedKeyPairFactory keyPairFactory;

    @Before
    public void setUp() {
        keyPairFactory = PregeneratedKeyPairFactory.getInstance();
        subject = new ProvisioningRequestProcessorBean(certificateAuthorityRepository, keyPairFactory, null,
                                                       commandService, validationStrategy);

        listCms = givenListResourceClassRequestCms();

        // set up mocks
        ProvisioningIdentityCertificate identityCertificate = mock(ProvisioningIdentityCertificate.class);
        when(identityCertificate.getPublicKey()).thenReturn(KeyPairFactoryTest.SECOND_TEST_KEY_PAIR.getPublic());
        when(child.getProvisioningIdentityCertificate()).thenReturn(identityCertificate);

        when(certificateAuthorityRepository.findByTypeAndUuid(ProductionCertificateAuthority.class,
                UUID.fromString(listCms.getPayload().getRecipient()), LockModeType.NONE)).thenReturn(parent);
        when(certificateAuthorityRepository.findByTypeAndUuid(NonHostedCertificateAuthority.class,
                UUID.fromString(listCms.getPayload().getSender()), LockModeType.PESSIMISTIC_WRITE)).thenReturn(child);

        when(child.getParent()).thenReturn(parent);
    }

    @Test
    public void shouldEnsureNonHostedMemberIsChildOfDelegationCa() {
        when(child.getParent()).thenReturn(null);

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

        when(certificateAuthorityRepository.findByTypeAndUuid(NonHostedCertificateAuthority.class, sender, LockModeType.PESSIMISTIC_WRITE)).thenReturn(child);

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

        when(certificateAuthorityRepository.findByTypeAndUuid(NonHostedCertificateAuthority.class, sender, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);

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
        DownStreamProvisioningCommunicator downStreamProvisioningCommunicator = mock(DownStreamProvisioningCommunicator.class);
        ArgumentCaptor<AbstractProvisioningPayload> captor = ArgumentCaptor.forClass(AbstractProvisioningPayload.class);
        when(parent.getMyDownStreamProvisioningCommunicator()).thenReturn(downStreamProvisioningCommunicator);

        subject.process(listCms);

        // payload was validated
        verify(validationStrategy).validateProvisioningCmsAndIdentityCertificate(eq(listCms), any());

        // object is signed
        verify(downStreamProvisioningCommunicator).createProvisioningCmsResponseObject(eq(keyPairFactory), captor.capture());
        AbstractProvisioningPayload responsePayload = captor.getValue();

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
