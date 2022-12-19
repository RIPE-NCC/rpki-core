package net.ripe.rpki.domain;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateProvisioningMessage;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.ports.ResourceLookupService;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChildCertificateAuthority {
    X500Principal getName();

    ParentCertificateAuthority getParent();

    Optional<ImmutableResourceSet> lookupCertifiableIpResources(ResourceLookupService resourceLookupService);

    Collection<PublicKey> getSignedPublicKeys();

    void processCertificateIssuanceResponse(CertificateIssuanceResponse response, ResourceCertificateRepository resourceCertificateRepository);

    void processCertificateRevocationResponse(CertificateRevocationResponse response,
                                              PublishedObjectRepository publishedObjectRepository,
                                              KeyPairDeletionService keyPairDeletionService);

    List<? extends CertificateProvisioningMessage> processResourceClassListResponse(
        ResourceClassListResponse response,
        CertificateRequestCreationService certificateRequestCreationService
    );
}
