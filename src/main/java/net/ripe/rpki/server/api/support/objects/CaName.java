package net.ripe.rpki.server.api.support.objects;

import com.google.common.base.Strings;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import javax.security.auth.x500.X500Principal;
import java.util.Locale;

@EqualsAndHashCode
public class CaName {

    private static final String ORG = "ORG-";
    private static final String CA_MEMBER_PREFIX = "CN=";
    private static final String CA_ORGANISATION_PREFIX = "O=";

    private final X500Principal principal;

    private static boolean isOfRightShape(String name) {
        if (name != null) {
            return name.startsWith(CA_MEMBER_PREFIX) || name.startsWith(CA_ORGANISATION_PREFIX);
        }
        return false;
    }

    private CaName(@NonNull final String name) {
        try {
            this.principal = new X500Principal(name);
        } catch (IllegalArgumentException e) {
            throw new BadCaNameException(e);
        }
    }

    public static CaName fromOrganisationId(final String organisationId) {
        String upperCase = Strings.nullToEmpty(organisationId).toUpperCase(Locale.ROOT);
        if (!upperCase.startsWith(ORG)) {
            throw new BadCaNameException("Organisation ID must start with ORG-");
        }
        return new CaName(CA_ORGANISATION_PREFIX + upperCase);
    }

    public static CaName fromMembershipId(final long memberShipId) {
        return new CaName(CA_MEMBER_PREFIX + memberShipId);
    }

    public static CaName of(final X500Principal user) {
        final String name = user.getName();
        if (!isOfRightShape(name)) {
            throw new BadCaNameException("Invalid CA principal name: " + name);
        }
        return new CaName(name);
    }

    public Long getMembershipId() {
        String encodedName = principal.toString();
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
        String encodedName = principal.toString();
        if (encodedName.startsWith(CA_ORGANISATION_PREFIX)) {
            return encodedName.replaceFirst(CA_ORGANISATION_PREFIX, "");
        }
        return null;
    }

    @NonNull
    public X500Principal getPrincipal() {
       return principal;
    }

    @Override
    public String toString() {
        return principal.toString();
    }

    public static CaName parse(String rawCaName) {
        String normalized = Strings.nullToEmpty(rawCaName).trim();
        if (normalized.isEmpty()) {
            throw new BadCaNameException("CA name cannot be empty");
        }
        if (isOfRightShape(normalized)) {
            return new CaName(normalized);
        }
        if (normalized.toUpperCase(Locale.ROOT).startsWith(ORG)) {
            return CaName.fromOrganisationId(normalized);
        }
        try {
            return CaName.fromMembershipId(Long.parseLong(normalized));
        } catch (IllegalArgumentException e) {
            throw new BadCaNameException("Invalid CA name: " + normalized);
        }
    }

    public static class BadCaNameException extends IllegalArgumentException {
        BadCaNameException(String message) {
            super(message);
        }

        BadCaNameException(Throwable cause) {
            super(cause);
        }
    }
}
