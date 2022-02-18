package net.ripe.rpki.services.impl.handlers;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.*;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.util.DBComponent;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChildParentCertificateUpdateSaga {

    private final KeyPairDeletionService keyPairDeletionService;

    private final CertificateRequestCreationService certificateRequestCreationService;
    private final PublishedObjectRepository publishedObjectRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final DBComponent dbComponent;
    private final ResourceLookupService resourceLookupService;
    private final KeyPairService keyPairService;

    public ChildParentCertificateUpdateSaga(KeyPairDeletionService keyPairDeletionService,
                                            CertificateRequestCreationService certificateRequestCreationService,
                                            PublishedObjectRepository publishedObjectRepository,
                                            ResourceCertificateRepository resourceCertificateRepository,
                                            DBComponent dbComponent,
                                            ResourceLookupService resourceLookupService,
                                            KeyPairService keyPairService) {
        this.keyPairDeletionService = keyPairDeletionService;
        this.certificateRequestCreationService = certificateRequestCreationService;
        this.publishedObjectRepository = publishedObjectRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.dbComponent = dbComponent;
        this.resourceLookupService = resourceLookupService;
        this.keyPairService = keyPairService;
    }

    public boolean execute(ParentCertificateAuthority parentCa, ChildCertificateAuthority childCa, int issuedCertificatesPerSignedKeyLimit) {
        Optional<IpResourceSet> childResources = childCa.lookupCertifiableIpResources(resourceLookupService);
        if (childResources.isPresent()) {
            return execute(parentCa, childCa, childResources.get(), issuedCertificatesPerSignedKeyLimit);
        }
        log.warn("Resource cache for CA is empty, exiting.");
        return false;
    }

    private boolean execute(ParentCertificateAuthority parentCa, ChildCertificateAuthority childCa, IpResourceSet childResources, int issuedCertificatesPerSignedKeyLimit) {
        // In the normal case there are no updates for incoming resource certificates, so check with the parent CA
        // without locking it (so we can check multiple child CAs concurrently using multiple threads).
        List<? extends CertificateProvisioningMessage> requests = checkIfUpdatedIsNeeded(parentCa, childCa, childResources, keyPairService);
        if (requests.isEmpty()) {
            // no effect
            return false;
        }

        // We may need to request a new certificate or revoke our current one, so re-lock the parent exclusively and retry
        // the check, since the parent CA may have been modified by a concurrent thread or command.
        dbComponent.lockAndRefresh(parentCa);
        requests = checkIfUpdatedIsNeeded(parentCa, childCa, childResources, keyPairService);

        for (final CertificateProvisioningMessage request : requests) {
            if (request instanceof CertificateIssuanceRequest) {
                final CertificateIssuanceResponse response = parentCa.processCertificateIssuanceRequest(
                    (CertificateIssuanceRequest) request, resourceCertificateRepository, dbComponent, issuedCertificatesPerSignedKeyLimit);
                childCa.processCertificateIssuanceResponse(response, resourceCertificateRepository);
            } else if (request instanceof CertificateRevocationRequest) {
                final CertificateRevocationResponse response = parentCa.processCertificateRevocationRequest(
                        (CertificateRevocationRequest) request, resourceCertificateRepository);
                childCa.processCertificateRevocationResponse(response, publishedObjectRepository, keyPairDeletionService);
            }
        }

        return !requests.isEmpty();
    }

    private List<? extends CertificateProvisioningMessage> checkIfUpdatedIsNeeded(ParentCertificateAuthority parentCa, ChildCertificateAuthority childCa, IpResourceSet childResources, KeyPairService keyPairService) {
        final ResourceClassListResponse resourceClassListResponse = parentCa.
            processResourceClassListQuery(new ResourceClassListQuery(childResources));

        final List<? extends CertificateProvisioningMessage> requests = childCa.processResourceClassListResponse(
                resourceClassListResponse, keyPairService, certificateRequestCreationService);

        DateTime now = new DateTime(DateTimeZone.UTC);
        ValidityPeriod validityPeriod = new ValidityPeriod(now, CertificateAuthority.calculateValidityNotAfter(now));

        return requests.stream()
            .filter((request) -> {
                if (request instanceof CertificateIssuanceRequest) {
                    return parentCa.isCertificateIssuanceNeeded((CertificateIssuanceRequest) request, validityPeriod, resourceCertificateRepository);
                } else if (request instanceof CertificateRevocationRequest) {
                    return parentCa.isCertificateRevocationNeeded((CertificateRevocationRequest) request, resourceCertificateRepository);
                } else {
                    throw new IllegalStateException("unknown request type " + request);
                }
            })
            .collect(Collectors.toList());
    }

}
