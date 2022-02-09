package net.ripe.rpki.rest.pojo;

import net.ripe.rpki.commons.validation.roa.RouteValidityState;

public class ROAWithAnnouncementStatus {

    private ROA roa;
    private RouteValidityState validity;

    public ROAWithAnnouncementStatus() {
    }

    public ROAWithAnnouncementStatus(ROA roa, RouteValidityState validity) {
        this.roa = roa;
        this.validity = validity;
    }

    public ROA getRoa() {
        return roa;
    }

    public void setRoa(ROA roa) {
        this.roa = roa;
    }

    public RouteValidityState getValidity() {
        return validity;
    }

    public void setValidity(RouteValidityState validity) {
        this.validity = validity;
    }

}
