package net.ripe.rpki.rest.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ROA {
    private String asn;
    private String prefix;
    // external API (and portal) use maximalLength (sic)
    @JsonProperty("maximalLength")
    private Integer maxLength;

    @Override
    public String toString() {
        return "ROA{" +
                "asn='" + asn + '\'' +
                ", prefix='" + prefix + '\'' +
                // maximalLength (sic) kept for consistency with API input
                ", maximalLength=" + maxLength +
                '}';
    }
}
