package net.ripe.rpki.domain.roa;

import jakarta.persistence.*;
import lombok.*;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.ripencc.support.persistence.AsnPersistenceConverter;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;


@EqualsAndHashCode
@Entity
@Table(name = "roaconfiguration_prefixes")
@IdClass(RoaConfigurationPrefix.RoaConfigurationPrefixIdClass.class)
public class RoaConfigurationPrefix {

    @NoArgsConstructor
    @Data
    public static class RoaConfigurationPrefixIdClass implements Serializable {
        // via https://stackoverflow.com/a/61258208
        @Column(name = "asn", nullable = false)
        @Convert(converter = AsnPersistenceConverter.class)
        private Asn asn;
        private BigInteger prefixStart;
        private BigInteger prefixEnd;
        @Column(name = "prefix_type_id", nullable = false)
        private IpResourceType prefixType;
    }

    @Id
    @Column(name = "asn", nullable = false)
    @Getter
    private Asn asn;

    @Id
    @Column(name = "prefix_start", nullable = false)
    private BigInteger prefixStart;

    @Id
    @Column(name = "prefix_end", nullable = false)
    private BigInteger prefixEnd;

    @Id
    @Column(name = "prefix_type_id", nullable = false)
    private IpResourceType prefixType;

    @Column(name = "maximum_length")
    private Integer maximumLength;

    @ManyToOne
    @JoinColumn(name = "roaconfiguration_id", nullable = false)
    @Setter
    private RoaConfiguration roaConfiguration;

    @Getter
    @Setter
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RoaConfigurationPrefix() {
        // JPA uses this
    }

    @PreUpdate
    @PrePersist
    void prePersist() {
        this.updatedAt = Instant.now();
    }

    public RoaConfigurationPrefix(Asn asn, IpRange prefix) {
        this(asn, prefix, null);
    }

    public RoaConfigurationPrefix(Asn asn, IpRange prefix, Integer maximumLength) {
        this(new RoaConfigurationPrefixData(asn, prefix, maximumLength));
    }

    public RoaConfigurationPrefix(Asn asn, IpRange prefix, Integer maximumLength, Instant updatedAt) {
        this(new RoaConfigurationPrefixData(asn, prefix, maximumLength, updatedAt));
    }

    public RoaConfigurationPrefix(RoaConfigurationPrefixData data) {
        this.asn = data.getAsn();
        this.prefixType = data.getPrefix().getType();
        this.prefixStart = data.getPrefix().getStart().getValue();
        this.prefixEnd = data.getPrefix().getEnd().getValue();
        this.maximumLength = data.getMaximumLength();
        this.updatedAt = data.getUpdatedAt();
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
        return new RoaConfigurationPrefixData(getAsn(), getPrefix(), getMaximumLength(), getUpdatedAt());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("asn", getAsn())
                .append("prefix", getPrefix())
                .append("maximumLength", getMaximumLength())
                .append("updatedAt", getUpdatedAt())
                .toString();
    }

    public static List<RoaConfigurationPrefix> fromData(List<? extends RoaConfigurationPrefixData> data) {
        return data.stream().map(RoaConfigurationPrefix::new).toList();
    }
}
