package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;

/**
 * UN-Subscribe an email address to alerts about BGP updates seen by RIS
 * that are invalidated by the CA's ROAs.
 */
@Getter
public class UnsubscribeFromRoaAlertCommand extends CertificateAuthorityCommand {

    private final String email;
    private final boolean notifyOnRoaChanges;

    public UnsubscribeFromRoaAlertCommand(VersionedId certificateAuthorityId, String email, boolean notifyOnRoaChanges) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.email = email;
        this.notifyOnRoaChanges = notifyOnRoaChanges;
    }

    @Override
    public String getCommandSummary() {
        var roaSummary = notifyOnRoaChanges ? " and ROA changes." : ".";
        return "Unsubscribed " + email + " from ROA alerts" + roaSummary;
    }
}
