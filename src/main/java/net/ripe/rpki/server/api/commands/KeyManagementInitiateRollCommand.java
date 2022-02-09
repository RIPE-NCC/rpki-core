package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;

/**
 * Instruct the CA initiate a key roll over for all its resource classes where:
 * - There is currently only one key in use, and that key is in current state
 * - And that key is older than the key age threshold (days) specified.
 * - Will request certificate for the new key with the *same resources* as the current key
 *
 * See step 1 & 2 of http://tools.ietf.org/html/rfc6489#section-2
 *
 * Note publication (step 3) and entering the staging period (step 4) will happen when the
 * sign requests generated here are processed.
 */
public class KeyManagementInitiateRollCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    private final int keyAgeThreshold;

    public KeyManagementInitiateRollCommand(VersionedId certificateAuthorityId, int keyAgeThreshold) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.SYSTEM);
        this.keyAgeThreshold = keyAgeThreshold;
    }

    public int getMaxAgeDays() {
        return keyAgeThreshold;
    }

    @Override
    public String getCommandSummary() {
        return "Initiated key roll over.";
    }
}
