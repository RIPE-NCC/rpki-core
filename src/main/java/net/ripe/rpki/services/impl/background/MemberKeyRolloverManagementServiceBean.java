package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("memberKeyRolloverManagementService")
public class MemberKeyRolloverManagementServiceBean extends AbstractKeyRolloverManagementServiceBean {

    public MemberKeyRolloverManagementServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                                  CertificationConfiguration certificationConfiguration,
                                                  CertificateAuthorityViewService certificationService,
                                                  CommandService commandService,
                                                  @Value("${keypair.keyroll.batch.size}") Integer batchSize) {
        super(backgroundTaskRunner, certificationConfiguration, certificationService, commandService, Optional.of(batchSize));
    }

    @Override
    public String getName() {
        return "Member Key Rollover Management Service";
    }

    @Override
    protected void runService() {
        runService(HostedCertificateAuthority.class);
    }
}
