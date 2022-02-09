package net.ripe.rpki.server.api.commands;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;



public class UpdateRoaAlertIgnoredAnnouncedRoutesCommand extends CertificateAuthorityModificationCommand {

    private static final long serialVersionUID = 1L;

    private final List<AnnouncedRoute> additions;

    private final List<AnnouncedRoute> deletions;

    public UpdateRoaAlertIgnoredAnnouncedRoutesCommand(VersionedId certificateAuthorityId, Collection<AnnouncedRoute> added, Collection<AnnouncedRoute> deleted) {
        super(certificateAuthorityId, CertificateAuthorityCommandGroup.USER);
        this.additions = new ArrayList<>(added);
        this.additions.sort(AnnouncedRoute.ASN_PREFIX_COMPARATOR);
        this.deletions = new ArrayList<>(deleted);
        this.deletions.sort(AnnouncedRoute.ASN_PREFIX_COMPARATOR);
    }

    public List<AnnouncedRoute> getAdditions() {
        return Collections.unmodifiableList(additions);
    }

    public List<AnnouncedRoute> getDeletions() {
        return Collections.unmodifiableList(deletions);
    }

    @Override
    public String getCommandSummary() {
        return "Updated suppressed routes for ROA alerts. " +
                "Additions: " + StringUtils.join(getHumanReadableAnnouncedRoutes(additions), ", ") + ". " +
                "Deletions: " + StringUtils.join(getHumanReadableAnnouncedRoutes(deletions), ", ") + ".";
    }

    private List<String> getHumanReadableAnnouncedRoutes(List<AnnouncedRoute> announcedRoutes) {
        List<String> announcedRoutesAsString = new ArrayList<>();
        for (AnnouncedRoute announcedRoute : announcedRoutes) {
            announcedRoutesAsString.add("[asn=" + announcedRoute.getOriginAsn() + ", prefix=" + announcedRoute.getPrefix() + "]");
        }
        if (announcedRoutesAsString.isEmpty()) {
            announcedRoutesAsString.add("none");
        }
        return announcedRoutesAsString;
    }
}
