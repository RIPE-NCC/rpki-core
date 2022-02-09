package net.ripe.rpki.domain.alerts;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import javax.persistence.Basic;
import javax.persistence.Embeddable;


@Embeddable
public class RoaAlertIgnoredAnnouncement extends ValueObjectSupport {
    private static final long serialVersionUID = 1L;

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
