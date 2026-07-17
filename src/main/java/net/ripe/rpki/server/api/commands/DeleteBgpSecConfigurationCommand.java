package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;

@Getter
public class DeleteBgpSecConfigurationCommand extends CertificateAuthorityModificationCommand {

    private final BgpSecConfigurationData removed;

    public DeleteBgpSecConfigurationCommand(VersionedId certificateAuthorityId,
                                            BgpSecConfigurationData removed) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.removed = removed;
    }

    @Override
    public String getCommandSummary() {
        return "Removed BGPSec configuration: " + removed + ".";
    }
}
