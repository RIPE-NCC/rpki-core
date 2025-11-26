
package net.ripe.rpki.server.api.services.command;

import net.ripe.ipresource.Asn;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This exception indicates that a roa config update commands is trying to add private ASNs.
 */
public class PrivateAsnsUsedException extends CertificationException {

    private static final long serialVersionUID = 1L;

    public PrivateAsnsUsedException(String type, List<Asn> privateAsns) {
        super(type + " has private ASN(s): " + formatAsns(privateAsns));
    }

    private static String formatAsns(List<Asn> asns) {
        return Set.copyOf(asns).stream().map(Asn::toString).collect(Collectors.joining(","));
    }
}
