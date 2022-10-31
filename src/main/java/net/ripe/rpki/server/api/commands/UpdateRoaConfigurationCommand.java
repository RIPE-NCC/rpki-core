package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class UpdateRoaConfigurationCommand extends CertificateAuthorityModificationCommand {

    private final List<RoaConfigurationPrefixData> additions;

    private final List<RoaConfigurationPrefixData> deletions;

    public UpdateRoaConfigurationCommand(VersionedId certificateAuthorityId, Collection<RoaConfigurationPrefixData> added, Collection<RoaConfigurationPrefixData> deleted) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.additions = new ArrayList<>(added);
        this.additions.sort(RoaConfigurationPrefixData.COMPARATOR);
        this.deletions = new ArrayList<>(deleted);
        this.deletions.sort(RoaConfigurationPrefixData.COMPARATOR);
    }

    public List<RoaConfigurationPrefixData> getAdditions() {
        return Collections.unmodifiableList(additions);
    }

    public List<RoaConfigurationPrefixData> getDeletions() {
        return Collections.unmodifiableList(deletions);
    }

    @Override
    public String getCommandSummary() {
        return "Updated ROA configuration. " +
                "Additions: " + StringUtils.join(getHumanReadableRoaPrefixData(additions), ", ") + ". " +
                "Deletions: " + StringUtils.join(getHumanReadableRoaPrefixData(deletions), ", ") + ".";
    }

    public static List<String> getHumanReadableRoaPrefixData(Collection<RoaConfigurationPrefixData> roaPrefixDataList) {
        List<String> roaPrefixDataAsString = new ArrayList<>();
        for (RoaConfigurationPrefixData roaPrefixData : roaPrefixDataList) {
            roaPrefixDataAsString.add("[asn=" + roaPrefixData.getAsn() + ", prefix=" + roaPrefixData.getPrefix() + ", maximumLength=" + roaPrefixData.getMaximumLength() + "]");
        }
        if (roaPrefixDataAsString.isEmpty()) {
            roaPrefixDataAsString.add("none");
        }
        return roaPrefixDataAsString;
    }
}
