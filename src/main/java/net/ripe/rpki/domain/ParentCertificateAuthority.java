package net.ripe.rpki.domain;

import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.ncc.core.domain.support.Entity;


/**
 * This interface limits the behavior of a CertificateAuthority when acting as
 * a parent. This interface mimics the "up-down" protocol specification.
 */
public interface ParentCertificateAuthority extends Entity {

    Long getId();

    ParentCertificateAuthority getParent();

    CertificateIssuanceResponse processCertificateIssuanceRequest(ChildCertificateAuthority requestingCa,
                                                                  CertificateIssuanceRequest request,
                                                                  ResourceCertificateRepository resourceCertificateRepository,
                                                                  int issuedCertificatesPerSignedKeyLimit);

    CertificateRevocationResponse processCertificateRevocationRequest(CertificateRevocationRequest request,
                                                                      ResourceCertificateRepository resourceCertificateRepository);

    ResourceClassListResponse processResourceClassListQuery(ResourceClassListQuery memberResources);

}
