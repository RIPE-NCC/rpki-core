package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.stereotype.Service;

import static net.ripe.rpki.services.impl.background.BackgroundServices.PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE;

@Service(PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE)
public class ProductionCaKeyRolloverManagementServiceBean extends AbstractKeyRolloverManagementServiceBean {


    public ProductionCaKeyRolloverManagementServiceBean(ActiveNodeService activeNodeService,
                                                        CertificationConfiguration certificationConfiguration,
                                                        CertificateAuthorityViewService certificationService,
                                                        CommandService commandService) {
        super(activeNodeService, certificationConfiguration, certificationService, commandService);
    }

    @Override
    public String getName() {
        return "ProductionCa Key Rollover Management Service";
    }

    @Override
    protected void runService() {
        runService(CertificateAuthorityType.ROOT);
    }

}
