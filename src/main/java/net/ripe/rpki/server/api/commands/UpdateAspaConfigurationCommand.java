package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.rest.service.Aspas;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class UpdateAspaConfigurationCommand extends CertificateAuthorityModificationCommand {

    private final String ifMatch;
    private final List<AspaConfigurationData> newConfigurations;
    private final Map<Asn, Aspas.AspaDiff> diffPerCustomer;

    public UpdateAspaConfigurationCommand(VersionedId certificateAuthorityId, String ifMatch,
                                          List<AspaConfigurationData> newConfigurations,
                                          Map<Asn, Aspas.AspaDiff> diffPerCustomer) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.ifMatch = ifMatch;
        this.newConfigurations = newConfigurations;
        this.diffPerCustomer = diffPerCustomer;
    }

    @Override
    public String getCommandSummary() {
        var diffString = diffPerCustomer.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().longValue()))
                .map(e -> {
                    var added = AspaConfigurationData.inIETFNotation(e.getKey(), e.getValue().added());
                    var deleted = asnList(e.getValue().deleted());
                    return added + " (was: " + deleted + ")";
                })
                .collect(Collectors.joining("; "));

        if (diffString.isEmpty()) {
            diffString = "no changes";
        }
        return "Updated ASPA configuration: " + diffString + ".";
    }

    private static String asnList(Collection<Asn> asns) {
        return asns.stream()
                .sorted(Comparator.comparing(Asn::longValue))
                .map(Asn::toString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public static String inIETFNotation(List<AspaConfigurationData> aspaConfiguration) {
        return aspaConfiguration.stream()
                .map(a -> AspaConfigurationData.inIETFNotation(a.getCustomerAsn(), a.getProviders()))
                .collect(Collectors.joining("; "));
    }

}
