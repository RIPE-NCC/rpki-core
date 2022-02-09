package net.ripe.rpki.domain.archive.test;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import net.ripe.rpki.server.api.dto.KeyPairData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.PublicKey;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyPairDeletionServiceTest {
    private static final String REVOKED_KEY = "key to be revoked";
    private static final String KEY_STORE_NAME = "keyStore";
    private static final PublicKey PUBLIC_KEY = TestObjects.createTestKeyPair().getPublicKey();
    private KeyPairDeletionService subject;

    @Mock
    private CrlEntityRepository crlEntityRepository;
    @Mock
    private ManifestEntityRepository manifestEntityRepository;
    @Mock
    private RoaEntityRepository roaRepository;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private PublishedObjectRepository publishedObjectRepository;
    @Mock
    private HostedCertificateAuthority ca;

    @Before
    public void initKeyPairArchivingService() {
        subject = new KeyPairDeletionService(crlEntityRepository,
                manifestEntityRepository,
                roaRepository,
                resourceCertificateRepository,
                publishedObjectRepository);
    }

    private Optional<KeyPairData> keyPairData(String keyStoreName) {
        KeyPairData keyPairData = new KeyPairData(null, null, keyStoreName, null, null, null, null, null, null, false);
        return Optional.of(keyPairData);
    }

    @Test
    public void should_ask_the_ca_to_archive_revoked_keys() {
        CertificateRevocationResponse revocationResponse = revocationResponse(PUBLIC_KEY);
        when(ca.deleteRevokedKey(keyFrom(revocationResponse), crlEntityRepository, manifestEntityRepository, roaRepository, resourceCertificateRepository, publishedObjectRepository)).thenReturn(keyPairData(KEY_STORE_NAME));

        subject.deleteRevokedKeysFromResponses(ca, Collections.singletonList(revocationResponse));

        verify(ca).deleteRevokedKey(matches(keyFrom(revocationResponse)), any(CrlEntityRepository.class), any(ManifestEntityRepository.class), any(RoaEntityRepository.class), any(ResourceCertificateRepository.class), any(PublishedObjectRepository.class));
    }

    private String keyFrom(CertificateRevocationResponse revocationResponse) {
        return KeyPairUtil.getEncodedKeyIdentifier(revocationResponse.getSubjectPublicKey());
    }

    private CertificateRevocationResponse revocationResponse(PublicKey publicKey) {
        return new CertificateRevocationResponse("RIPE", publicKey);
    }
}
