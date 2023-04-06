package net.ripe.rpki.domain.archive.test;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.aspa.AspaEntityRepository;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

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

    @InjectMocks
    private KeyPairDeletionService subject;

    @Test
    public void should_ask_the_ca_to_delete_revoked_keys() {
        subject.deleteRevokedKey(KEY_PAIR);

        verify(publishedObjectRepository).withdrawAllForDeletedKeyPair(KEY_PAIR);
        verify(crlEntityRepository).deleteByKeyPair(KEY_PAIR);
        verify(manifestEntityRepository).deleteByKeyPairEntity(KEY_PAIR);
        verify(roaEntityRepository).deleteByCertificateSigningKeyPair(KEY_PAIR);
        verify(aspaEntityRepository).deleteByCertificateSigningKeyPair(KEY_PAIR);
        verify(resourceCertificateRepository).deleteOutgoingCertificatesForRevokedKeyPair(KEY_PAIR);
    }

}
