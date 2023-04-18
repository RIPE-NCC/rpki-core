package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.IntermediateCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE;

@Service(PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE)
public class ProductionCaKeyRolloverManagementServiceBean extends AbstractKeyRolloverManagementServiceBean {


    private boolean intermediateCaEnabled;

    public ProductionCaKeyRolloverManagementServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                                        CertificationConfiguration certificationConfiguration,
                                                        CertificateAuthorityViewService certificationService,
                                                        CommandService commandService,
                                                        @Value("${intermediate.ca.enabled:false}") boolean intermediateCaEnabled) {
        super(backgroundTaskRunner, certificationConfiguration, certificationService, commandService, Optional.empty());
        this.intermediateCaEnabled = intermediateCaEnabled;
    }

    @Override
    public String getName() {
        return String.format("Production %sCA Key Rollover Management Service", intermediateCaEnabled ? "and Intermediate " : "");
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        runKeyRoll(ProductionCertificateAuthority.class, parameters);
        runKeyRoll(IntermediateCertificateAuthority.class, parameters);
    }

}
