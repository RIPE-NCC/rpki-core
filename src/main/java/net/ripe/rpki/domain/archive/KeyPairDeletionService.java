package net.ripe.rpki.domain.archive;

import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
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
    private final RoaEntityRepository roaRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final PublishedObjectRepository publishedObjectRepository;

    @Inject
    public KeyPairDeletionService(CrlEntityRepository crlEntityRepository,
                                  ManifestEntityRepository manifestEntityRepository,
                                  RoaEntityRepository roaRepository,
                                  ResourceCertificateRepository resourceCertificateRepository,
                                  PublishedObjectRepository publishedObjectRepository) {
        this.crlEntityRepository = crlEntityRepository;
        this.manifestEntityRepository = manifestEntityRepository;
        this.roaRepository = roaRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.publishedObjectRepository = publishedObjectRepository;
    }

    public void deleteRevokedKeysFromResponses(HostedCertificateAuthority ca, List<CertificateRevocationResponse> revocationResponses) {
        final List<String> encodedKeyIdentifiers = revocationResponses.stream()
                .map(response -> KeyPairUtil.getEncodedKeyIdentifier(response.getSubjectPublicKey()))
                .collect(Collectors.toList());
        deleteRevokedKeys(ca, encodedKeyIdentifiers);
    }

    public void deleteRevokedKeys(HostedCertificateAuthority ca, List<String> revokedKeys) {
        revokedKeys.forEach(revokedKey ->
            ca.deleteRevokedKey(revokedKey, crlEntityRepository, manifestEntityRepository,
                roaRepository, resourceCertificateRepository, publishedObjectRepository));
    }


}
