package net.ripe.rpki.domain;

import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.interca.CertificateRevocationRequest;
import net.ripe.rpki.domain.interca.CertificateRevocationResponse;
import net.ripe.rpki.ncc.core.domain.support.Entity;
import net.ripe.rpki.util.DBComponent;


/**
 * This interface limits the behavior of a CertificateAuthority when acting as
 * a parent. This interface mimics the "up-down" protocol specification.
 */
public interface ParentCertificateAuthority extends Entity {

    ParentCertificateAuthority getParent();

    boolean isCertificateIssuanceNeeded(CertificateIssuanceRequest request, ValidityPeriod validityPeriod, ResourceCertificateRepository resourceCertificateRepository);

    CertificateIssuanceResponse processCertificateIssuanceRequest(CertificateIssuanceRequest request,
                                                                  ResourceCertificateRepository resourceCertificateRepository,
                                                                  DBComponent dbComponent);

    boolean isCertificateRevocationNeeded(CertificateRevocationRequest request, ResourceCertificateRepository resourceCertificateRepository);

    CertificateRevocationResponse processCertificateRevocationRequest(CertificateRevocationRequest request,
                                                                      ResourceCertificateRepository resourceCertificateRepository);

    ResourceClassListResponse processResourceClassListQuery(ResourceClassListQuery memberResources);

}
