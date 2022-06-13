package net.ripe.rpki.server.api.dto;

import lombok.Getter;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import org.joda.time.Instant;

import javax.security.auth.x500.X500Principal;
import java.util.Set;
import java.util.UUID;

@Getter
public class NonHostedCertificateAuthorityData extends CertificateAuthorityData {

    private static final long serialVersionUID = 1L;

    private final ProvisioningIdentityCertificate provisioningIdentityCertificate;
    private final Instant lastSeenProvisioningMessageTime;
    private final Set<NonHostedPublicKeyData> publicKeys;

    public NonHostedCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid, Long parentId,
                                             ProvisioningIdentityCertificate provisioningIdentityCertificate,
                                             Instant lastSeenProvisioningMessageTime,
                                             IpResourceSet ipResourceSet,
                                             Set<NonHostedPublicKeyData> publicKeys) {
        super(versionedId, name, uuid, parentId, CertificateAuthorityType.NONHOSTED, ipResourceSet, null);

        this.provisioningIdentityCertificate = provisioningIdentityCertificate;
        this.lastSeenProvisioningMessageTime = lastSeenProvisioningMessageTime;
        this.publicKeys= publicKeys;
    }

}
