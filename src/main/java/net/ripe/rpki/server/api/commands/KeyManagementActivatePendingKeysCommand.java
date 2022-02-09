package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import org.joda.time.Duration;

/**
 * Instruct the CA to activate all keys that have been pending for at least the staging period
 * See step 5 of: http://tools.ietf.org/html/rfc6489#section-2
 */
public class KeyManagementActivatePendingKeysCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    private final long minStagingTimeMs;


    public static KeyManagementActivatePendingKeysCommand plannedActivationCommand(VersionedId certificateAuthorityId, Duration stagingPeriod) {
        return new KeyManagementActivatePendingKeysCommand(certificateAuthorityId, stagingPeriod);
    }

    public static KeyManagementActivatePendingKeysCommand manualActivationCommand(VersionedId certificateAuthorityId) {
        return new KeyManagementActivatePendingKeysCommand(certificateAuthorityId, Duration.standardHours(0));
    }

    private KeyManagementActivatePendingKeysCommand(VersionedId certificateAuthorityId, Duration minStagingTime) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.SYSTEM);
        this.minStagingTimeMs = minStagingTime.getMillis();
    }

    public Duration getMinStagingTime() {
        return new Duration(minStagingTimeMs);
    }

    @Override
    public String getCommandSummary() {
        return "Activated pending keys.";
    }
}
