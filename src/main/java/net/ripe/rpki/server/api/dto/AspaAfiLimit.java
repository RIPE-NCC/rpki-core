package net.ripe.rpki.server.api.dto;

import net.ripe.rpki.commons.crypto.rfc3779.AddressFamily;

import java.util.Optional;

/** See <a href="https://www.ietf.org/archive/id/draft-ietf-sidrops-aspa-profile-10.html#section-3.3.1.2">ASPA profile afiLimit</a> */
public enum AspaAfiLimit {
    /** The authorization is valid for both IPv4 and IPv6 announcements. */
    ANY,
    /** the authorization is valid only for IPv4 announcements. */
    IPv4,
    /** the authorization is valid only for IPv6 announcements. */
    IPv6;

    public static AspaAfiLimit fromOptionalAddressFamily(Optional<AddressFamily> maybeAddressFamily) {
        return maybeAddressFamily
            .map(addressFamily -> addressFamily == AddressFamily.IPV4 ? AspaAfiLimit.IPv4 : AspaAfiLimit.IPv6)
            .orElse(AspaAfiLimit.ANY);
    }

    public Optional<AddressFamily> toOptionalAddressFamily() {
        switch (this) {
            case ANY:
                return Optional.empty();
            case IPv4:
                return Optional.of(AddressFamily.IPV4);
            case IPv6:
                return Optional.of(AddressFamily.IPV6);
        }
        throw new IllegalStateException("unknown AspaAfiLimit " + this);
    }
}
