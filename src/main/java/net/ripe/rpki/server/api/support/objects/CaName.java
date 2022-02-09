package net.ripe.rpki.server.api.support.objects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.EqualsAndHashCode;

import javax.security.auth.x500.X500Principal;
import java.util.Locale;

@EqualsAndHashCode
public class CaName {

    private static final String ORG = "ORG-";
    private static final String CA_MEMBER_PREFIX = "CN=";
    private static final String CA_ORGANISATION_PREFIX = "O=";

    private final String encodedName;

    private static boolean isOfRightShape(String name) {
        if (name != null) {
            return name.startsWith(CA_MEMBER_PREFIX) || name.startsWith(CA_ORGANISATION_PREFIX);
        }
        return false;
    }

    private CaName(final String encodedName) {
        this.encodedName = Preconditions.checkNotNull(encodedName);
    }

    public static CaName of(final String organisationId) {
        Preconditions.checkArgument(!Strings.nullToEmpty(organisationId).trim().isEmpty(),
                                    "Organisation ID must not be null or empty");
        return new CaName(CA_ORGANISATION_PREFIX + organisationId.toUpperCase(Locale.ROOT));
    }

    public static CaName of(final long memberShipId) {
        return new CaName(CA_MEMBER_PREFIX + memberShipId);
    }

    public static CaName of(final X500Principal user) {
        final String name = user.getName().toUpperCase(Locale.ROOT);
        Preconditions.checkArgument(isOfRightShape(name), "Invalid principal name: %s", name);
        return new CaName(name);
    }

    public Long getMembershipId() {
        if (encodedName.startsWith(CA_MEMBER_PREFIX)) {
            final String rawMembershipId = encodedName.replaceFirst(CA_MEMBER_PREFIX, "");
            try {
                return Long.parseLong(rawMembershipId);
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    public boolean hasOrganizationId() {
        return getOrganisationId() != null;
    }

    public String getOrganisationId() {
        if (encodedName.startsWith(CA_ORGANISATION_PREFIX)) {
            return encodedName.replaceFirst(CA_ORGANISATION_PREFIX, "");
        }
        return null;
    }

    public X500Principal getPrincipal() {
       return new X500Principal(encodedName);
    }

    @Override
    public String toString() {
        return encodedName;
    }

    public static CaName parse(String rawCaName) {
        if (isOfRightShape(rawCaName)) {
            return new CaName(rawCaName);
        }
        if (rawCaName.startsWith(ORG)) {
            return CaName.of(rawCaName);
        }
        try {
            return CaName.of(Long.parseLong(rawCaName));
        } catch (NumberFormatException e) {
            throw new BadCaNameException("Invalid principal name: " + rawCaName);
        }
    }

    public static class BadCaNameException extends IllegalArgumentException {
        BadCaNameException(String message) {
            super(message);
        }
    }
}
