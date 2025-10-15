package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class UpdateAspaConfigurationCommand extends CertificateAuthorityModificationCommand {

    private final String ifMatch;
    private final List<AspaConfigurationData> configurations;

    public UpdateAspaConfigurationCommand(VersionedId certificateAuthorityId, String ifMatch, List<AspaConfigurationData> configurations) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.ifMatch = ifMatch;
        this.configurations = configurations;
    }

    @Override
    public String getCommandSummary() {
        var s = "Update ASPA configuration to: ";
        if (configurations.isEmpty()) {
            return s + "empty.";
        }
        return s + inIETFNotation(configurations) + ".";
    }

    public static String inIETFNotation(List<AspaConfigurationData> aspaConfiguration) {
        return aspaConfiguration.stream()
                .map(AspaConfigurationData::inIETFNotation)
                .collect(Collectors.joining("; "));
    }

}
