package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static net.ripe.rpki.services.impl.background.BackgroundServices.HOSTED_KEY_ROLLOVER_MANAGEMENT_SERVICE;

@Service(HOSTED_KEY_ROLLOVER_MANAGEMENT_SERVICE)
public class HostedCaKeyRolloverManagementServiceBean extends AbstractKeyRolloverManagementServiceBean {

    public HostedCaKeyRolloverManagementServiceBean(BackgroundTaskRunner backgroundTaskRunner,
                                                    CertificationConfiguration certificationConfiguration,
                                                    CertificateAuthorityViewService certificationService,
                                                    CommandService commandService,
                                                    @Value("${keypair.keyroll.batch.size}") Integer batchSize) {
        super(backgroundTaskRunner, certificationConfiguration, certificationService, commandService, Optional.of(batchSize));
    }

    @Override
    public String getName() {
        return "Hosted CA Key Rollover Management Service";
    }

    @Override
    protected void runService() {
        runService(HostedCertificateAuthority.class);
    }
}
