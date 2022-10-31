package net.ripe.rpki.domain.archive;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.aspa.AspaEntityRepository;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class KeyPairDeletionService {

    private final CrlEntityRepository crlEntityRepository;
    private final ManifestEntityRepository manifestEntityRepository;
    private final RoaEntityRepository roaEntityRepository;
    private final AspaEntityRepository aspaEntityRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final PublishedObjectRepository publishedObjectRepository;

    @Inject
    public KeyPairDeletionService(CrlEntityRepository crlEntityRepository,
                                  ManifestEntityRepository manifestEntityRepository,
                                  RoaEntityRepository roaEntityRepository,
                                  AspaEntityRepository aspaEntityRepository,
                                  ResourceCertificateRepository resourceCertificateRepository,
                                  PublishedObjectRepository publishedObjectRepository) {
        this.crlEntityRepository = crlEntityRepository;
        this.manifestEntityRepository = manifestEntityRepository;
        this.roaEntityRepository = roaEntityRepository;
        this.aspaEntityRepository = aspaEntityRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.publishedObjectRepository = publishedObjectRepository;
    }

    public void deleteRevokedKeysFromResponses(ManagedCertificateAuthority ca, List<CertificateRevocationResponse> revocationResponses) {
        final List<String> encodedKeyIdentifiers = revocationResponses.stream()
                .map(response -> KeyPairUtil.getEncodedKeyIdentifier(response.getSubjectPublicKey()))
                .collect(Collectors.toList());
        deleteRevokedKeys(ca, encodedKeyIdentifiers);
    }

    public void deleteRevokedKeys(ManagedCertificateAuthority ca, List<String> revokedKeyIdentifiers) {
        revokedKeyIdentifiers.forEach(revokedKeyIdentifier ->
            ca.deleteRevokedKey(revokedKeyIdentifier, keyPair -> {
                publishedObjectRepository.withdrawAllForDeletedKeyPair(keyPair);
                crlEntityRepository.deleteByKeyPair(keyPair);
                manifestEntityRepository.deleteByKeyPairEntity(keyPair);
                roaEntityRepository.deleteByCertificateSigningKeyPair(keyPair);
                aspaEntityRepository.deleteByCertificateSigningKeyPair(keyPair);
                resourceCertificateRepository.deleteOutgoingCertificatesForRevokedKeyPair(keyPair);
            }));
    }

}
