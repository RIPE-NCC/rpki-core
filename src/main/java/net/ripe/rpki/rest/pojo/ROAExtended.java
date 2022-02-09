package net.ripe.rpki.rest.pojo;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(callSuper = true)
public class ROAExtended extends ROA {
    @SuppressWarnings({"java:S117", "java:S116"})
    @Getter @Setter private int _numberOfValidsCaused;
    @SuppressWarnings({"java:S117", "java:S116"})
    @Getter @Setter private int _numberOfInvalidsCaused;

    @SuppressWarnings("java:S117")
    public ROAExtended(String asn, String prefix, int maximalLength, int _numberOfValidsCaused, int _numberOfInvalidsCaused) {
        super(asn, prefix, maximalLength);
        this._numberOfValidsCaused = _numberOfValidsCaused;
        this._numberOfInvalidsCaused = _numberOfInvalidsCaused;
    }
}
