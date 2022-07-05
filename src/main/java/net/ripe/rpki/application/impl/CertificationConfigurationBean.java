package net.ripe.rpki.application.impl;

import lombok.Getter;
import net.ripe.rpki.application.CertificationConfiguration;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class CertificationConfigurationBean implements CertificationConfiguration {

    @Value("${autokeyrollover.maxage.days}")
    @Getter
    private int autoKeyRolloverMaxAgeDays;

    @Value("${keypair.activation.delay.hours}")
    private int keyPairActivationDelay;

    @Value("${key.management.data.directory}")
    @Getter
    private String keyManagementDataDirectory;

    @Value("${key.management.data.archive.directory}")
    @Getter
    private String keyManagementDataArchiveDirectory;

    @Value("${provisioning.base.url}")
    @Getter
    private String provisioningBaseUrl;

    @Value("${api-keys.properties}")
    @Getter
    private Resource apiKeys;

    @Override
    public Duration getStagingPeriod() {
        return Duration.standardHours(keyPairActivationDelay);
    }
}
