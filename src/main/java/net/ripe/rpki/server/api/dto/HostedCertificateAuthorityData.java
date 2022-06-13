package net.ripe.rpki.server.api.dto;

import lombok.Getter;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;

import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.UUID;

@Getter
public class HostedCertificateAuthorityData extends CertificateAuthorityData {

    private static final long serialVersionUID = 1L;
    private final List<KeyPairData> keys;

    public HostedCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid, Long parentId,
                                          CertificateAuthorityType type, IpResourceSet resources,
                                          TrustAnchorRequest trustAnchorRequest, List<KeyPairData> keys) {
        super(versionedId, name, uuid, parentId, type, resources, trustAnchorRequest);
        this.keys = keys;
    }

    public HostedCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid, Long parentId,
                                          CertificateAuthorityType type, IpResourceSet resources,
                                          List<KeyPairData> keys) {
        this(versionedId, name, uuid, parentId, type, resources, null, keys);
    }
}
