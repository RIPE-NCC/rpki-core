package net.ripe.rpki.domain;

import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ManagedCertificateAuthorityTest {

    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;

    private ManagedCertificateAuthority subject;
    private KeyPairEntity keyPair;

    @Before
    public void setUp() {
        subject = TestObjects.createInitialisedProdCaWithRipeResources();
        keyPair = subject.getCurrentKeyPair();
    }

    @Test
    public void should_not_create_revoke_request_for_current_key() {
        assertThat(keyPair.getStatus()).isEqualTo(KeyPairStatus.CURRENT);
        List<CertificateRevocationRequest> certificateRevocationRequests = subject.requestOldKeysRevocation(resourceCertificateRepository);

        assertThat(certificateRevocationRequests).isEmpty();
    }

    @Test
    public void should_create_revoke_request_for_old_keys_without_current_outgoing_resource_certificates() {
        keyPair.deactivate();
        when(resourceCertificateRepository.existsCurrentOutgoingCertificatesExceptForManifest(keyPair)).thenReturn(false);

        List<CertificateRevocationRequest> certificateRevocationRequests = subject.requestOldKeysRevocation(resourceCertificateRepository);

        assertThat(certificateRevocationRequests).hasSize(1);
    }

    @Test
    public void should_not_create_revoke_request_for_old_keys_with_current_outgoing_resource_certificates() {
        keyPair.deactivate();
        when(resourceCertificateRepository.existsCurrentOutgoingCertificatesExceptForManifest(keyPair)).thenReturn(true);

        List<CertificateRevocationRequest> certificateRevocationRequests = subject.requestOldKeysRevocation(resourceCertificateRepository);

        assertThat(certificateRevocationRequests).isEmpty();
    }

}
