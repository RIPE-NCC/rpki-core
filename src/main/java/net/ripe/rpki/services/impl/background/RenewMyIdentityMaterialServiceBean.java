package net.ripe.rpki.services.impl.background;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.server.api.commands.RenewMyIdentityMaterialCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.ProvisioningIdentityViewService;
import org.joda.time.Instant;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.util.Map;

@Service("renewMyIdentityMaterialServiceBean")
@Slf4j
public class RenewMyIdentityMaterialServiceBean extends SequentialBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final CommandService commandService;
    private final CertificateAuthorityViewService caViewService;
    private final RepositoryConfiguration repositoryConfiguration;

    private final ProvisioningIdentityViewService provisioningIdentityViewService;

    @Inject
    public RenewMyIdentityMaterialServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                              CommandService commandService,
                                              CertificateAuthorityViewService caViewService,
                                              ProvisioningIdentityViewService provisioningIdentityViewService,
                                              RepositoryConfiguration repositoryConfiguration) {
        super(backgroundTaskRunner);
        this.commandService = commandService;
        this.caViewService = caViewService;
        this.provisioningIdentityViewService = provisioningIdentityViewService;
        this.repositoryConfiguration = repositoryConfiguration;
    }

    @Override
    protected void runService(Map<String, String> parameters) {

        final X500Principal productionCaPrincipal = repositoryConfiguration.getProductionCaPrincipal();
        final CertificateAuthorityData maybeProductionCa = caViewService.findCertificateAuthorityByName(productionCaPrincipal);
        if (maybeProductionCa == null) {
            log.error("Production Certificate Authority '{}' does not exists.", productionCaPrincipal);
            return;
        }

        final CertificateAuthorityData productionCa = caViewService.findCertificateAuthorityByName(productionCaPrincipal);
        ProvisioningIdentityCertificate identityMaterial = provisioningIdentityViewService.findProvisioningIdentityMaterial();
        if (identityMaterial == null) {
            log.error("Identity material for Production Certificate Authority '{}' does not exists.", productionCaPrincipal);
            return;
        }

        Instant _30daysFromNow = Instant.now().plus(30 * 24 * 60 * 60 * 1000L);
        if (identityMaterial.getValidityPeriod().isExpiredAt(_30daysFromNow)) {
            commandService.execute(new RenewMyIdentityMaterialCommand(productionCa.getVersionedId()));
        } else {
            log.info("Provisioning identity certificate (for up-down communication) is not close to expiration {}, {}.",
                    identityMaterial.getValidityPeriod().getNotValidBefore(),
                    identityMaterial.getValidityPeriod().getNotValidAfter());
        }
    }

    @Override
    public String getName() {
        return "Renew up-down key material (certificate and CRL) for Production CA";
    }
}
