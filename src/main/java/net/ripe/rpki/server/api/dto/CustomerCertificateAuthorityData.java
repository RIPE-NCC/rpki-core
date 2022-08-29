package net.ripe.rpki.server.api.dto;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;

import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.UUID;

public class CustomerCertificateAuthorityData extends ManagedCertificateAuthorityData {
    public CustomerCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid, Long parentId, IpResourceSet resources, List<KeyPairData> keys) {
        super(versionedId, name, uuid, parentId, CertificateAuthorityType.HOSTED, resources, keys);
    }
}
