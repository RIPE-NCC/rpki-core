package net.ripe.rpki.server.api.commands;

import lombok.Getter;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.RoaPrefixData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.apache.commons.lang.StringUtils;

import java.util.*;


public class UpdateRoaConfigurationCommand extends CertificateAuthorityModificationCommand {

    /**
     * For backwards compatibility with the older client the use of the <code>If-Match</code> header is optional.
     * If this field is not provided the last writer wins.
     */
    @Getter
    private final Optional<String> ifMatch;

    private final List<RoaConfigurationPrefixData> additions;

    private final List<RoaConfigurationPrefixData> deletions;

    public UpdateRoaConfigurationCommand(VersionedId certificateAuthorityId, Optional<String> ifMatch, Collection<RoaConfigurationPrefixData> added, Collection<RoaConfigurationPrefixData> deleted) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.ifMatch = ifMatch;
        this.additions = new ArrayList<>(added);
        this.additions.sort(RoaPrefixData.ROA_PREFIX_DATA_COMPARATOR);
        this.deletions = new ArrayList<>(deleted);
        this.deletions.sort(RoaPrefixData.ROA_PREFIX_DATA_COMPARATOR);
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
