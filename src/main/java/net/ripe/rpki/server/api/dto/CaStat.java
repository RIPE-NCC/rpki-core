package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

public class CaStat extends ValueObjectSupport {

    public final String caName;

    public final int roas;

    public final String createdAt;

    public CaStat(String caName, int roaCount, String createdAt) {
        this.caName = caName;
        this.roas = roaCount;
        this.createdAt = createdAt;
    }
}

