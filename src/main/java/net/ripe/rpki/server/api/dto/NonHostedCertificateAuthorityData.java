package net.ripe.rpki.server.api.dto;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import org.joda.time.Instant;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

public class NonHostedCertificateAuthorityData extends CertificateAuthorityData {

    private static final long serialVersionUID = 1L;

    private final ProvisioningIdentityCertificate identityCertificate;
    private final Instant lastSeenProvisioningMessageTime;

    public NonHostedCertificateAuthorityData(VersionedId versionedId, X500Principal name, UUID uuid,
                                             ProvisioningIdentityCertificate identityCertificate,
                                             Instant lastSeenProvisioningMessageTime,
                                             IpResourceSet ipResourceSet) {
        super(versionedId, name, uuid, CertificateAuthorityType.NONHOSTED, ipResourceSet, null);

        this.identityCertificate = identityCertificate;
        this.lastSeenProvisioningMessageTime = lastSeenProvisioningMessageTime;
    }

    public ProvisioningIdentityCertificate getIdentityCertificate() {
        return identityCertificate;
    }

    public Instant getLastSeenProvisioningMessageTime() {
        return lastSeenProvisioningMessageTime;
    }
}
