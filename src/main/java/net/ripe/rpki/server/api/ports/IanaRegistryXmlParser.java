package net.ripe.rpki.server.api.ports;

import net.ripe.ipresource.IpResourceSet;

public interface IanaRegistryXmlParser {

    enum  MajorityRir {
        AFRINIC, APNIC, ARIN, LACNIC, RIPE
    }

    IpResourceSet getRirResources(MajorityRir rir);
}
