package net.ripe.rpki.rest.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An intent for a Validated Roa Payload.
 *
 * This is <emph>one</emph> asn-prefix pair with an optional maxlength. This is <b>not</b> a ROA since a ROA is
 * 1:n mapping from ASN to prefixes and maxlengths.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ApiRoaPrefix {
    private String asn;
    private String prefix;
    // external API (and portal) use maximalLength (sic)
    @JsonProperty("maximalLength")
    private Integer maxLength;

    @Override
    public String toString() {
        // The term 'ROA' is kept to have a consistent API.
        return "ROA{" +
                "asn='" + asn + '\'' +
                ", prefix='" + prefix + '\'' +
                // maximalLength (sic) kept for consistency with API input
                ", maximalLength=" + maxLength +
                '}';
    }
}
