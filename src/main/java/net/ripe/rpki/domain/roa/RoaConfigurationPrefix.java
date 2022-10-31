package net.ripe.rpki.domain.roa;

import lombok.Getter;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.math.BigInteger;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.google.common.base.Objects.*;

@Embeddable
public class RoaConfigurationPrefix {
    @Column(name = "asn", nullable = false)
    @Getter
    private Asn asn;

    @Column(name = "prefix_start", nullable = false)
    private BigInteger prefixStart;

    @Column(name = "prefix_end", nullable = false)
    private BigInteger prefixEnd;

    @Column(name = "prefix_type_id", nullable = false)
    private IpResourceType prefixType;

    // Nullable for database compatibility reasons.
    @Column(name = "maximum_length", nullable = true)
    private Integer maximumLength;

    protected RoaConfigurationPrefix() {
        // JPA uses this
    }

    public RoaConfigurationPrefix(Asn asn, IpRange prefix) {
        this(asn, prefix, null);
    }

    public RoaConfigurationPrefix(Asn asn, IpRange prefix, Integer maximumLength) {
        this(new RoaConfigurationPrefixData(asn, prefix, maximumLength));
    }

    public RoaConfigurationPrefix(RoaConfigurationPrefixData data) {
        this.asn = data.getAsn();
        this.prefixType = data.getPrefix().getType();
        this.prefixStart = data.getPrefix().getStart().getValue();
        this.prefixEnd = data.getPrefix().getEnd().getValue();
        this.maximumLength = data.getMaximumLength();
    }

    public RoaConfigurationPrefix(AnnouncedRoute route, Integer maximumLength) {
        this(route.getOriginAsn(), route.getPrefix(), maximumLength);
    }

    public IpRange getPrefix() {
        return (IpRange) prefixType.fromBigInteger(prefixStart).upTo(prefixType.fromBigInteger(prefixEnd));
    }

    public int getMaximumLength() {
        return maximumLength == null ? getPrefix().getPrefixLength() : maximumLength;
    }

    public RoaConfigurationPrefixData toData() {
        return new RoaConfigurationPrefixData(getAsn(), getPrefix(), getMaximumLength());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getAsn().hashCode();
        result = prime * result + getPrefix().hashCode();
        result = prime * result + getMaximumLength();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        RoaConfigurationPrefix that = (RoaConfigurationPrefix) obj;
        return equal(getAsn(), that.getAsn())
                && equal(this.getPrefix(), that.getPrefix())
                && this.getMaximumLength() == that.getMaximumLength();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("asn", getAsn())
                .append("prefix", getPrefix())
                .append("maximumLength", getMaximumLength())
                .toString();
    }

    public static Collection<? extends RoaConfigurationPrefix> fromData(Collection<? extends RoaConfigurationPrefixData> data) {
        return data.stream().map(RoaConfigurationPrefix::new).collect(Collectors.toList());
    }
}
