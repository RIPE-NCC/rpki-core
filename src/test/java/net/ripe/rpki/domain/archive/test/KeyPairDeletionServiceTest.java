package net.ripe.rpki.domain.archive.test;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.aspa.AspaEntityRepository;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyPairDeletionServiceTest {
    public static final KeyPairEntity KEY_PAIR = TestObjects.createTestKeyPair();

    @Mock
    private CrlEntityRepository crlEntityRepository;
    @Mock
    private ManifestEntityRepository manifestEntityRepository;
    @Mock
    private RoaEntityRepository roaEntityRepository;
    @Mock
    private AspaEntityRepository aspaEntityRepository;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private PublishedObjectRepository publishedObjectRepository;
    @Mock
    private ManagedCertificateAuthority ca;

    @InjectMocks
    private KeyPairDeletionService subject;

    @Test
    @SuppressWarnings("unchecked")
    public void should_ask_the_ca_to_delete_revoked_keys() {
        CertificateRevocationResponse revocationResponse = new CertificateRevocationResponse("RIPE", KEY_PAIR.getPublicKey());

        when(ca.deleteRevokedKey(any(), any())).then(invocation -> {
            invocation.getArgument(1, Consumer.class).accept(KEY_PAIR);
            return Optional.of(KEY_PAIR);
        });

        subject.deleteRevokedKeysFromResponses(ca, Collections.singletonList(revocationResponse));

        verify(publishedObjectRepository).withdrawAllForDeletedKeyPair(KEY_PAIR);
        verify(crlEntityRepository).deleteByKeyPair(KEY_PAIR);
        verify(manifestEntityRepository).deleteByKeyPairEntity(KEY_PAIR);
        verify(roaEntityRepository).deleteByCertificateSigningKeyPair(KEY_PAIR);
        verify(aspaEntityRepository).deleteByCertificateSigningKeyPair(KEY_PAIR);
        verify(resourceCertificateRepository).deleteOutgoingCertificatesForRevokedKeyPair(KEY_PAIR);
    }

}
