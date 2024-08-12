package net.ripe.rpki.server.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix;
import net.ripe.rpki.commons.validation.roa.RoaPrefixData;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;

import java.time.Instant;

import static com.google.common.base.Objects.*;
import static com.google.common.base.Preconditions.*;

/**
 * A ROA prefix.
 *
 * JSON serialisation matches that expected in a 'validated objects' json.
 * <pre>
 *     ...
 *    {
 *      "asn": 49505,
 *      "prefix": "77.244.218.0/24",
 *      "maxLength": 24,
 *    },
 *    ...
 * </pre>
 */
public class RoaConfigurationPrefixData extends ValueObjectSupport implements RoaPrefixData {
    // cycle when serialised using default serialiser
    @Getter
    @JsonSerialize(using = ToStringSerializer.class)
    private final Asn asn;

    // cycle when serialised using default serialiser
    @Getter
    @JsonSerialize(using = ToStringSerializer.class)
    private final IpRange prefix;

    private final Integer maximumLength;

    /**
     * The point in time this prefix was last updated.
     *
     * No object can have a updatedAt before the point in time this feature was added.
     */
    @Getter
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Instant updatedAt;

    public RoaConfigurationPrefixData(Asn asn, RoaPrefix roaPrefix, Instant updatedAt) {
        this.asn = checkNotNull(asn, "asn is required");
        checkNotNull(roaPrefix, "roaPrefix is required");
        this.prefix = roaPrefix.getPrefix();
        this.maximumLength = roaPrefix.getMaximumLength();
        this.updatedAt = updatedAt;
    }

    public RoaConfigurationPrefixData(Asn asn, RoaPrefix roaPrefix) {
        this.asn = checkNotNull(asn, "asn is required");
        checkNotNull(roaPrefix, "roaPrefix is required");
        this.prefix = roaPrefix.getPrefix();
        this.maximumLength = roaPrefix.getMaximumLength();
        this.updatedAt = null;
    }

    public RoaConfigurationPrefixData(Asn asn, IpRange prefix, Integer maximumLength) {
        // Ensure correct validation of prefix and maximum length by using RoaPrefix constructor.
        this(asn, new RoaPrefix(prefix, maximumLength));
    }

    public RoaConfigurationPrefixData(Asn asn, IpRange prefix, Integer maximumLength, Instant updatedAt) {
        // Ensure correct validation of prefix and maximum length by using RoaPrefix constructor.
        this(asn, new RoaPrefix(prefix, maximumLength), updatedAt);
    }

    @JsonIgnore
    public RoaPrefix getRoaPrefix() {
        return new RoaPrefix(prefix, maximumLength);
    }

    @JsonProperty("maxLength")
    public int getMaximumLength() {
        return maximumLength == null ? getPrefix().getPrefixLength() : maximumLength;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 17;
        result = prime * result + getAsn().hashCode();
        result = prime * result + getMaximumLength();
        result = prime * result + getPrefix().hashCode();
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
        RoaConfigurationPrefixData that = (RoaConfigurationPrefixData) obj;
        return equal(this.getAsn(), that.getAsn()) && equal(this.getPrefix(), that.getPrefix()) && equal(this.getMaximumLength(), that.getMaximumLength());
    }
}
