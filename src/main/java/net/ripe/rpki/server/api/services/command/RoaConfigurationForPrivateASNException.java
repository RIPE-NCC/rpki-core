
package net.ripe.rpki.server.api.services.command;

import net.ripe.ipresource.Asn;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This exception indicates that a roa config update commands is trying to add private ASNs.
 */
public class RoaConfigurationForPrivateASNException extends CertificationException {

    private static final long serialVersionUID = 1L;

    private final List<Asn> privateAsns;
    public RoaConfigurationForPrivateASNException( List<Asn> privateAsns) {
        super("Roa configuration has private ASN(s): " + privateAsns.stream().map(Asn::toString).collect(Collectors.joining(",")));
        this.privateAsns = privateAsns;
    }
}
