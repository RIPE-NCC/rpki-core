package net.ripe.rpki.domain.signing;

import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;

import java.util.List;
import java.util.Optional;

public interface CertificateRequestCreationService {
    /**
     * Initiate Key Roll, IF:
     * = there is only key
     * = and this key is current
     * = and it is the same age or older than the maximum age
     */
    Optional<CertificateIssuanceRequest> initiateKeyRoll(ManagedCertificateAuthority ca, int maxAge);

    CertificateIssuanceRequest createCertificateIssuanceRequestForNewKeyPair(ManagedCertificateAuthority ca, ResourceExtension resourceExtension);

    List<CertificateIssuanceRequest> createCertificateIssuanceRequestForAllKeys(ManagedCertificateAuthority ca, ResourceExtension resourceExtension);

    List<SigningRequest> requestAllResourcesCertificate(AllResourcesCertificateAuthority ca);

    List<CertificateRevocationRequest> createCertificateRevocationRequestForAllKeys(ManagedCertificateAuthority ca);

    CertificateRevocationRequest createCertificateRevocationRequestForOldKey(ManagedCertificateAuthority ca);

    TrustAnchorRequest createTrustAnchorRequest(List<TaRequest> signingRequests);
}
