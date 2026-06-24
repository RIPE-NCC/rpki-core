package net.ripe.rpki.application.impl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.roa.RoaConfigurationRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Getter
public class CaDeletionServiceBean {
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final RoaConfigurationRepository roaConfigurationRepository;
    private final RoaAlertConfigurationRepository roaAlertConfigurationRepository;
    private final KeyPairDeletionService keyPairDeletionService;
    private final CommandAuditService commandAuditService;

    public CaDeletionServiceBean(CertificateAuthorityRepository certificateAuthorityRepository,
                                 ResourceCertificateRepository resourceCertificateRepository,
                                 RoaConfigurationRepository roaConfigurationRepository,
                                 RoaAlertConfigurationRepository roaAlertConfigurationRepository,
                                 KeyPairDeletionService keyPairDeletionService,
                                 CommandAuditService commandAuditService) {
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.roaConfigurationRepository = roaConfigurationRepository;
        this.roaAlertConfigurationRepository = roaAlertConfigurationRepository;
        this.keyPairDeletionService = keyPairDeletionService;
        this.commandAuditService = commandAuditService;
    }

    public void deleteCa(long caId) {
        final CertificateAuthority ca = certificateAuthorityRepository.get(caId);

        log.warn("deleting CA '{}' (id = {}, UUID = {})", ca.getName(), ca.getId(), ca.getUuid());

        ca.getSignedPublicKeys().forEach(publicKey -> {
            CertificateRevocationRequest request = new CertificateRevocationRequest(publicKey);
            CertificateRevocationResponse response = ca.getParent().processCertificateRevocationRequest(request, resourceCertificateRepository);
            ca.processCertificateRevocationResponse(response, keyPairDeletionService);
        });

        ca.asManagedCertificateAuthority().ifPresent(managedCa -> {
            // Remove the ROA (alert) configuration after deleting keys above, otherwise there will be no audit events
            // generated for the ROA (alert) configuration changes.
            roaConfigurationRepository.findByCertificateAuthority(managedCa).ifPresent(roaConfigurationRepository::remove);
            var roaAlertConfiguration = roaAlertConfigurationRepository.findByCertificateAuthorityIdOrNull(managedCa.getId());
            if (roaAlertConfiguration != null) {
                roaAlertConfigurationRepository.remove(roaAlertConfiguration);
            }
        });

        commandAuditService.deleteCommandsForCa(ca.getId());
        certificateAuthorityRepository.remove(ca);
    }
}
