package net.ripe.rpki.server.api.dto;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.bgpsec.Csr;
import net.ripe.rpki.domain.bgpsec.RouterId;

public record BgpSecConfigurationData(
    Long id,
    Asn asn,
    RouterId routerId,
    String csr,
    String keyIdentifier
){

    public static BgpSecConfigurationData from(Long id,
                                               Asn asn,
                                               RouterId routerId,
                                               String csr) {
        return new BgpSecConfigurationData(id, asn, routerId, csr, Csr.getKeyIdentifier(csr));
    }

    public boolean matches(Asn asn, RouterId routerId, String csr) {
        var ski = Csr.getKeyIdentifier(csr);
        return this.asn.equals(asn) && this.routerId.equals(routerId) && this.keyIdentifier.equalsIgnoreCase(ski);
    }
}
