package net.ripe.rpki.rest.pojo;

import net.ripe.rpki.commons.validation.roa.RouteValidityState;

public class BgpAnnouncementChange extends BgpAnnouncement {

    public final RouteValidityState futureState;
    public final Boolean affectedByChange;

    public BgpAnnouncementChange(String asn, String prefix, int visibility, boolean suppressed, RouteValidityState currentState,
                                 RouteValidityState futureState, final Boolean affectedByChange, final Boolean verified) {
        super(asn, prefix, visibility, currentState, suppressed, verified);
        this.futureState = futureState;
        this.affectedByChange = affectedByChange;
    }
}
