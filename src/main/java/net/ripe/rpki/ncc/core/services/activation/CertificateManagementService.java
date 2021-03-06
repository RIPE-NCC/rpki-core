package net.ripe.rpki.ncc.core.services.activation;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;

public interface CertificateManagementService {
    OutgoingResourceCertificate issueSingleUseEeResourceCertificate(HostedCertificateAuthority hostedCa, CertificateIssuanceRequest request,
                                                                    ValidityPeriod validityPeriod, KeyPairEntity signingKeyPair);

    void addOutgoingResourceCertificate(OutgoingResourceCertificate resourceCertificate);

    /**
     * Issues a new manifest and CRL for every publishable key pair, if needed.
     *
     * @return the number of publishable keys that have their manifest and CRL updated.
     */
    long updateManifestAndCrlIfNeeded(HostedCertificateAuthority certificateAuthority);

    boolean isManifestAndCrlUpdatedNeeded(HostedCertificateAuthority certificateAuthority);
}
