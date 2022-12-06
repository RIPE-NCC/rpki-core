package net.ripe.rpki.server.api.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;

import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Getter
public class ManagedCertificateAuthorityData extends CertificateAuthorityData {

    private final List<KeyPairData> keys;

    public ManagedCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid, Long parentId,
                                           CertificateAuthorityType type, ImmutableResourceSet resources,
                                           TrustAnchorRequest trustAnchorRequest, List<KeyPairData> keys) {
        super(versionedId, name, uuid, parentId, type, resources, trustAnchorRequest);
        this.keys = keys;
    }

    public ManagedCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid, Long parentId,
                                           CertificateAuthorityType type, ImmutableResourceSet resources,
                                           List<KeyPairData> keys) {
        this(versionedId, name, uuid, parentId, type, resources, null, keys);
    }
}
