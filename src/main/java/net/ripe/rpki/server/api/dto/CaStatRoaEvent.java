package net.ripe.rpki.server.api.dto;

public class CaStatRoaEvent extends CaStatEvent {

    public final Integer roasAdded;

    public final Integer roasDeleted;

    public CaStatRoaEvent(String caName, String date, int roasAdded, int roasDeleted) {
        super(caName, date);
        // prevent useless zeros in the object serialized to JSON
        this.roasAdded = roasAdded == 0 ? null : roasAdded;
        this.roasDeleted = roasDeleted == 0 ? null : roasDeleted;
    }

}

