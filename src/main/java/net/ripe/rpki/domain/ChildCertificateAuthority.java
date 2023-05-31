package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateProvisioningMessage;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChildCertificateAuthority {
    X500Principal getName();

    ParentCertificateAuthority getParent();

    Optional<ResourceExtension> lookupCertifiableIpResources(ResourceLookupService resourceLookupService) throws ResourceInformationNotAvailableException;

    Collection<PublicKey> getSignedPublicKeys();

    boolean processCertificateIssuanceResponse(CertificateIssuanceResponse response, ResourceCertificateRepository resourceCertificateRepository);

    boolean processCertificateRevocationResponse(CertificateRevocationResponse response, KeyPairDeletionService keyPairDeletionService);

    List<? extends CertificateProvisioningMessage> processResourceClassListResponse(
        ResourceClassListResponse response,
        CertificateRequestCreationService certificateRequestCreationService
    );

    /**
     * Changes the parent of this CA. This is a dangerous operation since the child should no longer have any
     * active certificates with the current parent and will not have any active certificates with the new parent.
     * So the caller must ensure that any certificates are first revoked from the old parent and new certificates
     * are re-issued by the new parent.
     *
     * @param newParent the new parent.
     */
    void switchParentTo(ParentCertificateAuthority newParent);
}
