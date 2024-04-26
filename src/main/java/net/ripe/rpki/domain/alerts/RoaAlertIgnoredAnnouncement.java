package net.ripe.rpki.domain.alerts;

import lombok.Data;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;

import jakarta.persistence.Basic;
import jakarta.persistence.Embeddable;

@Data
@Embeddable
public class RoaAlertIgnoredAnnouncement {
    @Basic(optional = false)
    private String asn;

    @Basic(optional = false)
    private String prefix;

    public RoaAlertIgnoredAnnouncement() {
    }

    public RoaAlertIgnoredAnnouncement(AnnouncedRoute data) {
        this.asn = data.getOriginAsn().toString();
        this.prefix = data.getPrefix().toString();
    }

    public AnnouncedRoute toData() {
        return new AnnouncedRoute(Asn.parse(asn), IpRange.parse(prefix));
    }
}
