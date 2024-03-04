package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;

/**
 * UN-Subscribe an email address to alerts about BGP updates seen by RIS
 * that are invalidated by the CA's ROAs. 
 */
public class UnsubscribeFromRoaAlertCommand extends CertificateAuthorityCommand {

    @Getter
    private final String email;

    public UnsubscribeFromRoaAlertCommand(VersionedId certificateAuthorityId, String email) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.email = email;
    }

    @Override
    public String getCommandSummary() {
        return "Unsubscribed " + email + " from ROA alerts.";
    }
}
