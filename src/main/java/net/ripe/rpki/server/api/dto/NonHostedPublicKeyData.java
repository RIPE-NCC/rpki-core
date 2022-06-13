package net.ripe.rpki.server.api.dto;

import lombok.NonNull;
import lombok.Value;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.domain.RequestedResourceSets;

import java.io.Serializable;
import java.security.PublicKey;

@Value
public class NonHostedPublicKeyData implements Serializable {
    @NonNull PublicKey publicKey;
    @NonNull PayloadMessageType latestProvisioningRequestType;
    @NonNull RequestedResourceSets requestedResourceSets;
    ResourceCertificateData currentCertificate;
}
