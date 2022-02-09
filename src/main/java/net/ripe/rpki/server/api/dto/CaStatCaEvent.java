package net.ripe.rpki.server.api.dto;

public class CaStatCaEvent extends CaStatEvent {

    public String event;

    public CaStatCaEvent(String caName, String date, String what) {
        super(caName, date);
        this.event = what;
    }

    public static CaStatCaEvent created(String caName, String date) {
        return new CaStatCaEvent(caName, date, "created");
    }

    public static CaStatCaEvent deleted(String date) {
        return new CaStatCaEvent("UNKNOWN", date, "deleted");
    }

}

