package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.springframework.stereotype.Service;

@Service("memberKeyRolloverManagementService")
public class MemberKeyRolloverManagementServiceBean extends AbstractKeyRolloverManagementServiceBean {

    public MemberKeyRolloverManagementServiceBean(ActiveNodeService activeNodeService,
                                                  CertificationConfiguration certificationConfiguration,
                                                  CertificateAuthorityViewService certificationService,
                                                  CommandService commandService) {
        super(activeNodeService, certificationConfiguration, certificationService, commandService);
    }

    @Override
    public String getName() {
        return "Member Key Rollover Management Service";
    }

    @Override
    protected void runService() {
        runService(CertificateAuthorityType.HOSTED);
    }
}
