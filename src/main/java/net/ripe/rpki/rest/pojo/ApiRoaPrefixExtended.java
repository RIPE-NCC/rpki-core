package net.ripe.rpki.rest.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
public class ApiRoaPrefixExtended extends ApiRoaPrefix {
    @JsonProperty("_numberOfValidsCaused")
    @Getter @Setter private int numberOfValidsCaused;

    @JsonProperty("_numberOfInvalidsCaused")
    @Getter @Setter private int numberOfInvalidsCaused;

    @JsonProperty("_updatedAt")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @Getter @Setter private Instant updatedAt;

    @SuppressWarnings("java:S117")
    public ApiRoaPrefixExtended(String asn, String prefix, int maximalLength, int numberOfValidsCaused, int numberOfInvalidsCaused, @Nullable Instant updatedAt) {
        super(asn, prefix, maximalLength);
        this.numberOfValidsCaused = numberOfValidsCaused;
        this.numberOfInvalidsCaused = numberOfInvalidsCaused;
        this.updatedAt = updatedAt;
    }
}
