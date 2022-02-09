package net.ripe.rpki.domain.signing;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.ta.domain.request.SigningRequest;
import net.ripe.rpki.commons.ta.domain.request.TaRequest;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;

import java.util.List;

public interface CertificateRequestCreationService {
    /**
     * Initiate Key Roll, IF:
     * = there is only key
     * = and this key is current
     * = and it is the same age or older than the maxage
     *
     * Returns empty list of signing requests if nothing happened
     */
    CertificateIssuanceRequest initiateKeyRoll(int maxAge,
                                               KeyPairService keyPairService,
                                               HostedCertificateAuthority ca);

    List<CertificateIssuanceRequest> createCertificateIssuanceRequestForAllKeys(HostedCertificateAuthority ca, IpResourceSet certifiableResources);

    List<SigningRequest> requestProductionCertificates(IpResourceSet certifiableResources,
                                                       HostedCertificateAuthority ca);

    List<CertificateRevocationRequest> createCertificateRevocationRequestForAllKeys(HostedCertificateAuthority ca);

    CertificateRevocationRequest createCertificateRevocationRequestForOldKey(HostedCertificateAuthority ca);

    TrustAnchorRequest createTrustAnchorRequest(List<TaRequest> signingRequests);
}
