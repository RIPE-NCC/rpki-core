package net.ripe.rpki.application;

import org.joda.time.Duration;
import org.springframework.core.io.Resource;

public interface CertificationConfiguration {

	String getProvisioningBaseUrl();

	int getMaxSerialIncrement();

	int getAutoKeyRolloverMaxAgeDays();
    Duration getStagingPeriod();

    String getKeyManagementDataDirectoryOrNull();
    String getKeyManagementDataArchiveDirectoryOrNull();

    Resource getApiKeys();
}
