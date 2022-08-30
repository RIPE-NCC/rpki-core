package net.ripe.rpki.services.impl.handlers;

import com.google.common.collect.Iterators;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.*;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChildParentCertificateUpdateSaga {

    private final KeyPairDeletionService keyPairDeletionService;

    private final CertificateRequestCreationService certificateRequestCreationService;
    private final PublishedObjectRepository publishedObjectRepository;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final ResourceLookupService resourceLookupService;
    private final ConcurrentMap<X500Principal, Integer> overclaimingResourcesCounts = new ConcurrentHashMap<>();

    public ChildParentCertificateUpdateSaga(KeyPairDeletionService keyPairDeletionService,
                                            CertificateRequestCreationService certificateRequestCreationService,
                                            PublishedObjectRepository publishedObjectRepository,
                                            ResourceCertificateRepository resourceCertificateRepository,
                                            ResourceLookupService resourceLookupService,
                                            MeterRegistry meterRegistry) {
        this.keyPairDeletionService = keyPairDeletionService;
        this.certificateRequestCreationService = certificateRequestCreationService;
        this.publishedObjectRepository = publishedObjectRepository;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.resourceLookupService = resourceLookupService;
        Gauge.builder("rpkicore.overclaiming.cas", overclaimingResourcesCounts::size)
            .description("number of CAs that would have over-claiming resources")
            .register(meterRegistry);
    }

    public boolean execute(ChildCertificateAuthority childCa, int issuedCertificatesPerSignedKeyLimit) {
        List<? extends CertificateProvisioningMessage> requests = checkIfUpdatedIsNeeded(childCa);

        ParentCertificateAuthority parentCa = childCa.getParent();
        for (final CertificateProvisioningMessage request : requests) {
            if (request instanceof CertificateIssuanceRequest) {
                final CertificateIssuanceResponse response = parentCa.processCertificateIssuanceRequest(
                    childCa, (CertificateIssuanceRequest) request, resourceCertificateRepository, issuedCertificatesPerSignedKeyLimit);
                childCa.processCertificateIssuanceResponse(response, resourceCertificateRepository);
            } else if (request instanceof CertificateRevocationRequest) {
                final CertificateRevocationResponse response = parentCa.processCertificateRevocationRequest(
                        (CertificateRevocationRequest) request, resourceCertificateRepository);
                childCa.processCertificateRevocationResponse(response, publishedObjectRepository, keyPairDeletionService);
            }
        }

        return !requests.isEmpty();
    }

    public boolean isUpdateNeeded(ChildCertificateAuthority childCa) {
        return !checkIfUpdatedIsNeeded(childCa).isEmpty();
    }

    private List<? extends CertificateProvisioningMessage> checkIfUpdatedIsNeeded(ChildCertificateAuthority childCa) {
        Optional<IpResourceSet> maybeChildResources = childCa.lookupCertifiableIpResources(resourceLookupService);

        if (!maybeChildResources.isPresent()) {
            log.warn("Resource cache for CA is empty, exiting.");
            return Collections.emptyList();
        }

        IpResourceSet childResources = new IpResourceSet(maybeChildResources.get());

        // Do not remove resources that are still on outgoing resource certificates for child CAs, since this can
        // lead to an invalid repository state.
        IpResourceSet currentOutgoingChildCertificateResources = resourceCertificateRepository.findCurrentOutgoingChildCertificateResources(childCa.getName());

        if (childResources.contains(currentOutgoingChildCertificateResources)) {
            this.overclaimingResourcesCounts.remove(childCa.getName());
        } else {
            IpResourceSet overclaimingResources = new IpResourceSet(currentOutgoingChildCertificateResources);
            overclaimingResources.removeAll(childResources);

            this.overclaimingResourcesCounts.put(childCa.getName(), Iterators.size(overclaimingResources.iterator()));

            log.warn(
                "Not removing resources {} for CA {} since these are still on issued child CA certificate(s)",
                overclaimingResources,
                childCa.getName()
            );

            childResources.addAll(overclaimingResources);
        }

        ParentCertificateAuthority parentCa = childCa.getParent();

        final ResourceClassListResponse resourceClassListResponse = parentCa.
            processResourceClassListQuery(new ResourceClassListQuery(childResources));

        final List<? extends CertificateProvisioningMessage> requests = childCa.processResourceClassListResponse(
                resourceClassListResponse, certificateRequestCreationService);

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
