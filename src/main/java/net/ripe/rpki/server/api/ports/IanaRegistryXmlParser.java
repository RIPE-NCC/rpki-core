package net.ripe.rpki.server.api.ports;

import net.ripe.ipresource.ImmutableResourceSet;

public interface IanaRegistryXmlParser {

    enum  MajorityRir {
        AFRINIC, APNIC, ARIN, LACNIC, RIPE
    }

    ImmutableResourceSet getRirResources(MajorityRir rir);
}
