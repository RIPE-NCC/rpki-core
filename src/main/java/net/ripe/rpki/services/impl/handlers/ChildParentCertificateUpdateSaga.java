package net.ripe.rpki.services.impl.handlers;

import com.google.common.collect.Iterators;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.interca.*;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.ports.ResourceInformationNotAvailableException;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class ChildParentCertificateUpdateSaga {

    private final KeyPairDeletionService keyPairDeletionService;

    private final CertificateRequestCreationService certificateRequestCreationService;
    private final ResourceCertificateRepository resourceCertificateRepository;
    private final ResourceLookupService resourceLookupService;
    private final ConcurrentMap<X500Principal, Integer> overclaimingResourcesCounts = new ConcurrentHashMap<>();

    public ChildParentCertificateUpdateSaga(KeyPairDeletionService keyPairDeletionService,
                                            CertificateRequestCreationService certificateRequestCreationService,
                                            ResourceCertificateRepository resourceCertificateRepository,
                                            ResourceLookupService resourceLookupService,
                                            MeterRegistry meterRegistry) {
        this.keyPairDeletionService = keyPairDeletionService;
        this.certificateRequestCreationService = certificateRequestCreationService;
        this.resourceCertificateRepository = resourceCertificateRepository;
        this.resourceLookupService = resourceLookupService;
        Gauge.builder("rpkicore.overclaiming.cas", overclaimingResourcesCounts::size)
            .description("number of CAs that would have over-claiming resources")
            .register(meterRegistry);
    }

    public boolean execute(ChildCertificateAuthority childCa, int issuedCertificatesPerSignedKeyLimit) {
        List<? extends CertificateProvisioningMessage> requests = checkIfUpdatedIsNeeded(childCa);

        ParentCertificateAuthority parentCa = childCa.getParent();

        boolean hasEffect = false;
        for (CertificateProvisioningMessage request : requests) {
            boolean updated;
            if (request instanceof CertificateIssuanceRequest) {
                final CertificateIssuanceResponse response = parentCa.processCertificateIssuanceRequest(
                    childCa, (CertificateIssuanceRequest) request, resourceCertificateRepository, issuedCertificatesPerSignedKeyLimit
                );
                updated = childCa.processCertificateIssuanceResponse(response, resourceCertificateRepository);
            } else if (request instanceof CertificateRevocationRequest) {
                final CertificateRevocationResponse response = parentCa.processCertificateRevocationRequest(
                        (CertificateRevocationRequest) request, resourceCertificateRepository
                );
                updated = childCa.processCertificateRevocationResponse(response, keyPairDeletionService);
            } else {
                throw new IllegalArgumentException("unknown certificate provisioning message type " + request.getClass().getSimpleName());
            }
            hasEffect |= updated;
        }
        return hasEffect;
    }

    private List<? extends CertificateProvisioningMessage> checkIfUpdatedIsNeeded(ChildCertificateAuthority childCa) {
        Optional<ResourceExtension> maybeChildResources;
        try {
            maybeChildResources = childCa.lookupCertifiableIpResources(resourceLookupService);
        } catch (ResourceInformationNotAvailableException e) {
            log.warn("Resource cache for CA '{}' is null (not: empty), exiting.", childCa.getName());
            return Collections.emptyList();
        }

        // Do not remove resources that are still on outgoing resource certificates for child CAs, since this can
        // lead to an invalid repository state.
        ImmutableResourceSet currentOutgoingChildCertificateResources = resourceCertificateRepository.findCurrentOutgoingChildCertificateResources(childCa.getName());

        EnumSet<IpResourceType> childInheritedResourceTypes = maybeChildResources.map(ResourceExtension::getInheritedResourceTypes).orElse(EnumSet.noneOf(IpResourceType.class));
        for (IpResourceType inheritedResourceType : childInheritedResourceTypes) {
            // Inherited resources are never overclaiming due to parent-child invariant that the parent never removes resources
            // that are still on any outgoing child certificates.
            currentOutgoingChildCertificateResources = currentOutgoingChildCertificateResources.difference(ImmutableResourceSet.of(inheritedResourceType.getMinimum().upTo(inheritedResourceType.getMaximum())));
        }

        ImmutableResourceSet childResources = maybeChildResources.map(ResourceExtension::getResources).orElse(ImmutableResourceSet.empty());
        if (childResources.contains(currentOutgoingChildCertificateResources)) {
            this.overclaimingResourcesCounts.remove(childCa.getName());
        } else {
            ImmutableResourceSet overclaimingResources = currentOutgoingChildCertificateResources.difference(childResources);

            this.overclaimingResourcesCounts.put(childCa.getName(), Iterators.size(overclaimingResources.iterator()));

            log.warn(
                "Not removing resources {} for CA {} since these are still on issued child CA certificate(s)",
                overclaimingResources,
                childCa.getName()
            );

            childResources = childResources.union(overclaimingResources);
        }

        if (!childResources.isEmpty()) {
            maybeChildResources = Optional.of(ResourceExtension.of(childInheritedResourceTypes, childResources));
        }

        ParentCertificateAuthority parentCa = childCa.getParent();

        final ResourceClassListResponse resourceClassListResponse = parentCa.
            processResourceClassListQuery(new ResourceClassListQuery(maybeChildResources));

        return childCa.processResourceClassListResponse(resourceClassListResponse, certificateRequestCreationService);
    }

}
