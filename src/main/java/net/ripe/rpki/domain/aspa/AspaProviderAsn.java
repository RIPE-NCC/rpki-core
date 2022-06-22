package net.ripe.rpki.domain.aspa;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.server.api.dto.AspaProviderAsnData;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.math.BigInteger;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class AspaProviderAsn {

    @Column(name = "asn", nullable = false)
    private BigInteger asn;

    @Column(name = "prefix_type_id", nullable = false)
    private IpResourceType prefixType;

    public AspaProviderAsnData toData() {
        return new AspaProviderAsnData(new Asn(asn), prefixType.getCode());
    }
}
