package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;

import java.util.Set;

@Getter
public class UpdateRoaChangeAlertCommand extends CertificateAuthorityCommand {

    private final boolean notifyOnRoaChanges;
    private final Set<String> emails;

    public UpdateRoaChangeAlertCommand(VersionedId certificateAuthorityId, Set<String> emails, boolean notifyOnRoaChanges) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.notifyOnRoaChanges = notifyOnRoaChanges;
        this.emails = emails;
    }

    @Override
    public String getCommandSummary() {
        // Make the order predictable
        var emailStr = String.join(", ", emails.stream().sorted().toList());
        var action = notifyOnRoaChanges ?
                "Subscribed " + emailStr + " for" :
                "Unsubscribed " + emailStr + " from";
        return action + " ROA change alerts.";
    }
}
