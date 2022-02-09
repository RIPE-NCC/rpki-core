package net.ripe.rpki.services.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.crl.CrlEntity;
import net.ripe.rpki.domain.crl.CrlEntityRepository;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.manifest.ManifestEntity;
import net.ripe.rpki.domain.manifest.ManifestEntityRepository;
import net.ripe.rpki.domain.roa.RoaEntity;
import net.ripe.rpki.domain.roa.RoaEntityRepository;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;

@Slf4j
@Component
public class DeleteCertificateAuthorityService {

    private final CertificateAuthorityRepository caRepository;
    private final RoaEntityRepository roaEntityRepository;
    private final ManifestEntityRepository manifestEntityRepository;
    private final CrlEntityRepository crlEntityRepository;
    private final CommandAuditService commandAuditService;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final PublishedObjectRepository publishedObjectRepository;
    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;

    @Inject
    public DeleteCertificateAuthorityService(CertificateAuthorityRepository caRepository,
                                             RoaEntityRepository roaEntityRepository,
                                             ManifestEntityRepository manifestEntityRepository,
                                             CrlEntityRepository crlEntityRepository,
                                             CommandAuditService commandAuditService,
                                             ResourceCertificateRepository resourceCertificateRepository,
                                             PublishedObjectRepository publishedObjectRepository,
                                             RoaAlertConfigurationRepository roaAlertConfigurationRepository
    ) {
        this.caRepository = caRepository;
        this.roaEntityRepository = roaEntityRepository;
        this.manifestEntityRepository = manifestEntityRepository;
        this.crlEntityRepository = crlEntityRepository;
        this.commandAuditService = commandAuditService;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.publishedObjectRepository = publishedObjectRepository;
        this.roaAlertConfigurationRepository = roaAlertConfigurationRepository;
    }

    public void deleteNonHosted(long id) {
        final NonHostedCertificateAuthority nonHostedCa = caRepository.findNonHostedCa(id);
        if (nonHostedCa != null) {
            for (PublicKeyEntity publicKey : nonHostedCa.getPublicKeys()) {
                CertificateRevocationRequest certificateRevocationRequest = new CertificateRevocationRequest(publicKey.getPublicKey());
                nonHostedCa.getParent().processCertificateRevocationRequest(certificateRevocationRequest, resourceCertificateRepository);
            }
            caRepository.remove(nonHostedCa);
        } else {
            log.warn("Could not find non-hosted CA with id " + id);
        }
    }

    public void deleteCa(long id) {
        final HostedCertificateAuthority hostedCa = caRepository.findHostedCa(id);
        if (hostedCa != null) {

            log.warn("Deleting hosted CA with id " + id);

            hostedCa.getKeyPairs().forEach(this::deleteArtifactsOfKeyPairEntity);

            commandAuditService.deleteCommandsForCa(hostedCa.getId());

            final RoaAlertConfiguration roaAlertConfiguration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(hostedCa.getId());
            if (roaAlertConfiguration != null) {
                roaAlertConfigurationRepository.remove(roaAlertConfiguration);
            }
            caRepository.remove(hostedCa);


        } else {
            log.warn("Could not find hosted CA with id " + id);

            deleteNonHosted(id);
        }
    }

    private void deleteArtifactsOfKeyPairEntity(KeyPairEntity keyPair) {
        publishedObjectRepository.withdrawAllForDeletedKeyPair(keyPair);

        final List<RoaEntity> roas = roaEntityRepository.findByCertificateSigningKeyPair(keyPair);
        for (RoaEntity roa : roas) {
            roaEntityRepository.remove(roa);
        }

        final ManifestEntity mft = manifestEntityRepository.findByKeyPairEntity(keyPair);
        if (mft != null) {
            manifestEntityRepository.remove(mft);
        }

        final CrlEntity crl = crlEntityRepository.findByKeyPair(keyPair);
        if (crl != null) {
            crlEntityRepository.remove(crl);
        }

        for (final OutgoingResourceCertificate outgoingCert : resourceCertificateRepository.findCurrentCertificatesBySubjectPublicKey(keyPair.getPublicKey())) {
            outgoingCert.revoke();
        }

        final Collection<OutgoingResourceCertificate> resourceCertificates = resourceCertificateRepository.findAllBySigningKeyPair(keyPair);
        for (final OutgoingResourceCertificate resourceCertificate : resourceCertificates) {
            resourceCertificateRepository.remove(resourceCertificate);
        }

        keyPair.deleteIncomingResourceCertificate();
    }
}
