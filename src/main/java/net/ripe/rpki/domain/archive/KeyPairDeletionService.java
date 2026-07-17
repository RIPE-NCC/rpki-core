package net.ripe.rpki.domain.archive;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.aspa.AspaEntityRepository;
import net.ripe.rpki.domain.bgpsec.BgpSecEntityRepository;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

@Component
public class KeyPairDeletionService {

    private final CrlEntityRepository crlEntityRepository;
    private final ManifestEntityRepository manifestEntityRepository;
    private final RoaEntityRepository roaEntityRepository;
    private final AspaEntityRepository aspaEntityRepository;
    private final BgpSecEntityRepository bgpSecEntityRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final PublishedObjectRepository publishedObjectRepository;

    @Inject
    public KeyPairDeletionService(CrlEntityRepository crlEntityRepository,
                                  ManifestEntityRepository manifestEntityRepository,
                                  RoaEntityRepository roaEntityRepository,
                                  AspaEntityRepository aspaEntityRepository,
                                  BgpSecEntityRepository bgpSecEntityRepository,
                                  ResourceCertificateRepository resourceCertificateRepository,
                                  PublishedObjectRepository publishedObjectRepository) {
        this.crlEntityRepository = crlEntityRepository;
        this.manifestEntityRepository = manifestEntityRepository;
        this.roaEntityRepository = roaEntityRepository;
        this.aspaEntityRepository = aspaEntityRepository;
        this.bgpSecEntityRepository = bgpSecEntityRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.publishedObjectRepository = publishedObjectRepository;
    }

    public void deleteRevokedKey(KeyPairEntity keyPair) {
        publishedObjectRepository.withdrawAllForDeletedKeyPair(keyPair);
        crlEntityRepository.deleteByKeyPair(keyPair);
        manifestEntityRepository.deleteByKeyPairEntity(keyPair);
        roaEntityRepository.deleteByCertificateSigningKeyPair(keyPair);
        aspaEntityRepository.deleteByCertificateSigningKeyPair(keyPair);
        bgpSecEntityRepository.deleteByCertificateSigningKeyPair(keyPair);
        resourceCertificateRepository.deleteOutgoingCertificatesForRevokedKeyPair(keyPair);
    }

}
