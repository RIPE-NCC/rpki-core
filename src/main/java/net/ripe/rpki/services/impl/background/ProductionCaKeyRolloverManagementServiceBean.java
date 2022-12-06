package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE;

@Service(PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE)
public class ProductionCaKeyRolloverManagementServiceBean extends AbstractKeyRolloverManagementServiceBean {


    public ProductionCaKeyRolloverManagementServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                                        CertificationConfiguration certificationConfiguration,
                                                        CertificateAuthorityViewService certificationService,
                                                        CommandService commandService) {
        super(backgroundTaskRunner, certificationConfiguration, certificationService, commandService, Optional.empty());
    }

    @Override
    public String getName() {
        return "Production CA Key Rollover Management Service";
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        runKeyRoll(ProductionCertificateAuthority.class, parameters);
    }

}
