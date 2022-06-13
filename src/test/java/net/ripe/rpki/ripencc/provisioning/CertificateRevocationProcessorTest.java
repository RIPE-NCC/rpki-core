package net.ripe.rpki.ripencc.provisioning;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.payload.error.NotPerformedError;
import net.ripe.rpki.commons.provisioning.payload.revocation.CertificateRevocationKeyElement;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayload;
import net.ripe.rpki.commons.provisioning.payload.revocation.request.CertificateRevocationRequestPayloadBuilder;
import net.ripe.rpki.commons.provisioning.payload.revocation.response.CertificateRevocationResponsePayload;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateRevocationCommand;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedPublicKeyData;
import net.ripe.rpki.server.api.services.command.CommandService;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.PublicKey;
import java.util.Collections;
import java.util.UUID;

import static net.ripe.rpki.domain.Resources.DEFAULT_RESOURCE_CLASS;
import static net.ripe.rpki.ripencc.provisioning.CertificateIssuanceProcessorTest.NON_HOSTED_CA_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CertificateRevocationProcessorTest {

    @Mock
    private CommandService commandService;
    private NonHostedCertificateAuthorityData nonHostedCertificateAuthority;
    private PublicKey publicKey;

    private CertificateRevocationProcessor processor;

    @Before
    public void setUp() {
        publicKey = TestObjects.TEST_KEY_PAIR_2.getPublicKey();
        nonHostedCertificateAuthority = new NonHostedCertificateAuthorityData(
            new VersionedId(1234L, 1), NON_HOSTED_CA_NAME, UUID.randomUUID(), 1L,
            ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT,
            Instant.now(),
            new IpResourceSet(),
            Collections.singleton(new NonHostedPublicKeyData(publicKey, PayloadMessageType.issue, new RequestedResourceSets(), null))
        );
        processor = new CertificateRevocationProcessor(null, commandService);
    }

    @Test
    public void shouldProcessCertificateRevocationRequest() {
        String publicKeyHash = KeyPairUtil.getEncodedKeyIdentifier(publicKey);
        CertificateRevocationRequestPayload requestPayload = createPayload(publicKeyHash);

        CertificateRevocationResponsePayload response = processor.process(
            nonHostedCertificateAuthority, requestPayload);

        verify(commandService, times(1)).execute(isA(ProvisioningCertificateRevocationCommand.class));
        assertThat(response).isNotNull();
        assertThat(response.getKeyElement().getClassName()).isEqualTo(DEFAULT_RESOURCE_CLASS);
        assertThat(response.getKeyElement().getPublicKeyHash()).isEqualTo(publicKeyHash);
    }

    @Test
    public void should_reject_bad_resource_class() {
        CertificateRevocationRequestPayload request = new CertificateRevocationRequestPayload(
            new CertificateRevocationKeyElement("BAD", "unused-hash")
        );

        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, request))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REQ_NO_SUCH_RESOURCE_CLASS);
            });
    }

    @Test
    public void should_reject_unknown_public_key_hash() {
        CertificateRevocationRequestPayload request = new CertificateRevocationRequestPayload(
            new CertificateRevocationKeyElement(DEFAULT_RESOURCE_CLASS, "unknown-key-hash")
        );

        assertThatThrownBy(() -> processor.process(nonHostedCertificateAuthority, request))
            .isInstanceOfSatisfying(NotPerformedException.class, (error) -> {
                assertThat(error.getNotPerformedError()).isEqualTo(NotPerformedError.REV_NO_SUCH_KEY);
            });
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
