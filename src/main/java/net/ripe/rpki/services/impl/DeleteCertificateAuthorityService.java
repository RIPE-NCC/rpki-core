package net.ripe.rpki.services.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.PublicKeyEntity;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Collection;
import java.util.HashSet;

@Slf4j
@Component
public class DeleteCertificateAuthorityService {

    private final CertificateAuthorityRepository caRepository;
    private final CommandAuditService commandAuditService;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final PublishedObjectRepository publishedObjectRepository;
    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;
    private final KeyPairDeletionService keyPairDeletionService;

    @Inject
    public DeleteCertificateAuthorityService(CertificateAuthorityRepository caRepository,
                                             KeyPairDeletionService keyPairDeletionService,
                                             CommandAuditService commandAuditService,
                                             ResourceCertificateRepository resourceCertificateRepository,
                                             PublishedObjectRepository publishedObjectRepository,
                                             RoaAlertConfigurationRepository roaAlertConfigurationRepository
    ) {
        this.caRepository = caRepository;
        this.keyPairDeletionService = keyPairDeletionService;
        this.commandAuditService = commandAuditService;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.publishedObjectRepository = publishedObjectRepository;
        this.roaAlertConfigurationRepository = roaAlertConfigurationRepository;
    }

    public void revokeNonHosted(long id) {
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

    public void revokeCa(long id) {
        final HostedCertificateAuthority hostedCa = caRepository.findHostedCa(id);
        if (hostedCa != null) {
            log.warn("Deleting hosted CA with id " + id);

            // List of keypairs is duplicated to prevent concurrent modification of underlying collection during iteration, e.g during key rollover when a CA has multiple keypairs
            final Collection<KeyPairEntity> keyPairs = new HashSet<>(hostedCa.getKeyPairs());
            keyPairs.forEach(keyPair -> deleteArtifactsOfKeyPairEntity(hostedCa, keyPair));

            commandAuditService.deleteCommandsForCa(hostedCa.getId());

            final RoaAlertConfiguration roaAlertConfiguration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(hostedCa.getId());
            if (roaAlertConfiguration != null) {
                roaAlertConfigurationRepository.remove(roaAlertConfiguration);
            }
            caRepository.remove(hostedCa);


        } else {
            log.warn("Could not find hosted CA with id " + id);

            revokeNonHosted(id);
        }
    }

    private void deleteArtifactsOfKeyPairEntity(HostedCertificateAuthority ca, KeyPairEntity keyPair) {
        CertificateRevocationRequest certificateRevocationRequest = new CertificateRevocationRequest(keyPair.getPublicKey());
        CertificateRevocationResponse certificateRevocationResponse = ca.getParent().processCertificateRevocationRequest(certificateRevocationRequest, resourceCertificateRepository);
        ca.processCertificateRevocationResponse(certificateRevocationResponse, publishedObjectRepository, keyPairDeletionService);
    }
}
