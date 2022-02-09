package net.ripe.rpki.server.api.services.read;

import net.ripe.ipresource.IpResourceSet;

public interface ResourceCertificateViewService {
    // ResourceCertificates

    IpResourceSet findCertifiedResources(Long caId);

}
