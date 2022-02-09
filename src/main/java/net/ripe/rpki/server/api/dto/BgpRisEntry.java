package net.ripe.rpki.server.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import org.apache.commons.lang.Validate;

@EqualsAndHashCode
@Data
public final class BgpRisEntry {

    private final Asn origin;
    private final IpRange prefix;
    private final int visibility;

    public BgpRisEntry(Asn origin, IpRange prefix, int visibility) {
        Validate.notNull(origin, "origin is required");
        Validate.isTrue(prefix.isLegalPrefix(), "prefix must be a legal prefix");
        Validate.isTrue(visibility > 0, "Visibility must be a positive int");
        this.origin = origin;
        this.prefix = prefix;
        this.visibility = visibility;
    }
}
