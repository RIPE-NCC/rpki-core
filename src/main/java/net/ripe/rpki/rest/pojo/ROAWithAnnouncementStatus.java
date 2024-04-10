package net.ripe.rpki.rest.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;

public class ROAWithAnnouncementStatus {
    /**
     * Use legacy name in JSON serialization
     */
    @JsonProperty("roa")
    private ApiRoaPrefix roa;
    private RouteValidityState validity;

    public ROAWithAnnouncementStatus() {
    }

    public ROAWithAnnouncementStatus(ApiRoaPrefix roa, RouteValidityState validity) {
        this.roa = roa;
        this.validity = validity;
    }

    public ApiRoaPrefix getRoa() {
        return roa;
    }

    public void setRoa(ApiRoaPrefix roa) {
        this.roa = roa;
    }

    public RouteValidityState getValidity() {
        return validity;
    }

    public void setValidity(RouteValidityState validity) {
        this.validity = validity;
    }

}
