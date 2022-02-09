package net.ripe.rpki.rest.pojo;

import net.ripe.rpki.commons.validation.roa.RouteValidityState;

public class BgpAnnouncement {

    private String asn;
    private String prefix;
    private int visibility;
    private RouteValidityState currentState;
    private boolean suppressed;
    private boolean verified;

    public BgpAnnouncement() {
    }

    public BgpAnnouncement(String asn, String prefix, int visibility, RouteValidityState currentState, boolean suppressed) {
        this.asn = asn;
        this.prefix = prefix;
        this.visibility = visibility;
        this.currentState = currentState;
        this.suppressed = suppressed;
        this.verified = false;
    }

    public BgpAnnouncement(String asn, String prefix, int visibility, RouteValidityState currentState, boolean suppressed, boolean verified) {
        this(asn, prefix, visibility, currentState, suppressed);
        this.verified = verified;
    }

    public String getAsn() {
        return asn;
    }

    public void setAsn(String asn) {
        this.asn = asn;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getVisibility() {
        return visibility;
    }

    public void setVisibility(int visibility) {
        this.visibility = visibility;
    }

    public RouteValidityState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(RouteValidityState currentState) {
        this.currentState = currentState;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    public boolean isVerified() {
        return verified;
    }
}
