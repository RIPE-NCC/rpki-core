package net.ripe.rpki.server.api.services.read;

import lombok.NonNull;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.server.api.dto.ResourceCertificateData;

import java.security.PublicKey;
import java.util.Optional;

public interface ResourceCertificateViewService {
    // ResourceCertificates

    ImmutableResourceSet findCertifiedResources(Long caId);

    Optional<ResourceCertificateData> findCurrentIncomingResourceCertificate(long caId);

    Optional<ResourceCertificateData> findCurrentOutgoingResourceCertificate(long requestingCaId, @NonNull PublicKey subjectPublicKey);
}
