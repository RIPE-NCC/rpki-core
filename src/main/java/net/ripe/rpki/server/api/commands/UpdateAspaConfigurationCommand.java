package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class UpdateAspaConfigurationCommand extends CertificateAuthorityModificationCommand {

    private final String ifMatch;
    private final List<AspaConfigurationData> configuration;

    public UpdateAspaConfigurationCommand(VersionedId certificateAuthorityId, String ifMatch, List<AspaConfigurationData> configuration) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.ifMatch = ifMatch;
        this.configuration = configuration;
    }

    @Override
    public String getCommandSummary() {
        return "Update ASPA configuration to: " + getHumanReadableAspaConfiguration(configuration) + ".";
    }

    public static String getHumanReadableAspaConfiguration(List<AspaConfigurationData> aspaConfiguration) {
        // This string representation is stored in the command audit table and shown to the user
        return aspaConfiguration.stream()
            .map(aspa -> aspa.getCustomerAsn() + " -> " + getHumanReadableProviders(aspa))
            .collect(Collectors.joining("; "));
    }

    private static String getHumanReadableProviders(AspaConfigurationData aspa) {
        return aspa.getProviders().stream()
            .map(provider -> provider.getProviderAsn() + " [" + provider.getAfiLimit() + "]")
            .collect(Collectors.joining(", "));
    }
}
