package net.ripe.rpki.application.impl;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.domain.CertificationProviderConfigurationData;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class CertificationConfigurationBean implements CertificationConfiguration {

    @Value("${max.serial.increment}")
    private String maxSerialIncrementString;

    @Value("${autokeyrollover.maxage.days}")
    private int autoKeyRolloverMaxAgeDays;

    @Value("${keypair.activation.delay.hours}")
    private int keyPairActivationDelay;

    @Value("${key.management.data.directory}")
    private String keyManagementDataDirectory;

    @Value("${key.management.data.archive.directory}")
    private String keyManagementDataArchiveDirectory;

    @Value("${provisioning.base.url}")
    private String provisioningBaseUrl;

    @Value("${api-keys.properties}")
    private Resource apiKeys;

    @Override
    public int getMaxSerialIncrement() {
        return Integer.parseInt(maxSerialIncrementString);
    }

    @Override
    public int getAutoKeyRolloverMaxAgeDays() {
        return autoKeyRolloverMaxAgeDays;
    }

    @Override
    public Duration getStagingPeriod() {
        return Duration.standardHours(keyPairActivationDelay);
    }

    @Override
    public String getKeyManagementDataDirectoryOrNull() {
        return keyManagementDataDirectory;
    }

    @Override
    public String getKeyManagementDataArchiveDirectoryOrNull() {
        return keyManagementDataArchiveDirectory;
    }

    @Override
    public Resource getApiKeys() {
        return apiKeys;
    }

    @Override
    public String getProvisioningBaseUrl() {
        return provisioningBaseUrl;
    }
}
