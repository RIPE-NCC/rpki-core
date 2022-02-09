package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

public class CaStatEvent extends ValueObjectSupport {

    public final String caName;

    public final String date;

    public CaStatEvent(String caName, String date) {
        this.caName = caName;
        this.date = date;
    }
}
