package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;

@Getter
public class UpdateRoaChangeAlertCommand extends CertificateAuthorityCommand {

    private final boolean notifyOnRoaChanges;

    public UpdateRoaChangeAlertCommand(VersionedId certificateAuthorityId, boolean notifyOnRoaChanges) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.notifyOnRoaChanges = notifyOnRoaChanges;
    }

    @Override
    public String getCommandSummary() {
        var action = notifyOnRoaChanges ? "Subscribed " : "Unsubscribed ";
        return action + " from ROA change alerts.";
    }
}
