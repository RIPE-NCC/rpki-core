package net.ripe.rpki.core.read.services.ca;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.HostedCertificateAuthority;
import net.ripe.rpki.domain.ParentCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.ripencc.provisioning.ProvisioningAuditLogService;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.security.auth.x500.X500Principal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly=true)
public class CertificateAuthorityViewServiceImpl implements CertificateAuthorityViewService {

    private final EntityManager entityManager;
    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final CommandAuditService commandAuditService;
    private final ProvisioningAuditLogService provisioningAuditLogService;

    @Inject
    public CertificateAuthorityViewServiceImpl(EntityManager entityManager,
                                               CertificateAuthorityRepository certificateAuthorityRepository,
                                               CommandAuditService commandAuditService,
                                               ProvisioningAuditLogService provisioningAuditLogService) {
        this.entityManager = entityManager;
        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.commandAuditService = commandAuditService;
        this.provisioningAuditLogService = provisioningAuditLogService;
    }

    @Override
    public CertificateAuthorityData findCertificateAuthorityByName(X500Principal caName) {
        CertificateAuthority ca = certificateAuthorityRepository.findByName(caName);
        return ca != null ? convertToCaData(ca) : null;
    }

    @Override
    public Long findCertificateAuthorityIdByName(X500Principal name) {
        CertificateAuthority ca = certificateAuthorityRepository.findByName(name);
        return ca == null ? null : ca.getVersionedId().getId();
    }


    @Override
    public Long findCertificateAuthorityIdByTypeAndName(CertificateAuthorityType type, X500Principal name) {
        CertificateAuthority ca = certificateAuthorityRepository.findByTypeAndName(resolveClassForType(type), name);
        return ca == null ? null : ca.getVersionedId().getId();
    }

    @Override
    public CertificateAuthorityData findCertificateAuthority(Long caId) {
        CertificateAuthority ca = certificateAuthorityRepository.findHostedCa(caId);
        return convertToCaData(ca);
    }

    @Override
    public Collection<CertificateAuthorityData> findAllChildrenForCa(X500Principal caName) {
        HostedCertificateAuthority parent = certificateAuthorityRepository.findByTypeAndName(HostedCertificateAuthority.class, caName);
        return certificateAuthorityRepository.findAllByParent(parent).stream()
                .map(this::convertToCaData)
                .collect(Collectors.toList());
    }

    // Optimized version of the above to avoid all the resources heavy-lifting
    // when it's not needed.
    @Override
    public Collection<CaIdentity> findAllChildrenIdsForCa(X500Principal productionCaName) {
        HostedCertificateAuthority parent = certificateAuthorityRepository.findByTypeAndName(ProductionCertificateAuthority.class, productionCaName);
        return certificateAuthorityRepository.findAllByParent(parent).stream()
            .map(ca -> new CaIdentity(ca.getVersionedId(), CaName.of(ca.getName())))
            .collect(Collectors.toList());
    }

    @Override
    public Map<CaIdentity, IpResourceSet> findAllChildrenResourcesForCa(X500Principal productionCaName) {
        HostedCertificateAuthority parent = certificateAuthorityRepository.findByTypeAndName(ProductionCertificateAuthority.class, productionCaName);
        return certificateAuthorityRepository.findAllResourcesByParent(parent);
    }

    @Override
    public Collection<CertificateAuthorityData> findAllHostedCertificateAuthorities() {
        return findCertificateAuthoritiesMatchingType(CertificateAuthorityType.ROOT, CertificateAuthorityType.HOSTED, CertificateAuthorityType.ALL_RESOURCES);
    }

    private List<CertificateAuthorityData> findCertificateAuthoritiesMatchingType(CertificateAuthorityType... allowedType) {
        final List<CertificateAuthorityType> allowedTypes = Arrays.asList(allowedType);
        return certificateAuthorityRepository.findAll().stream()
                .filter(ca -> allowedTypes.contains(ca.getType()))
                .map(this::convertToCaData)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommandAuditData> findMostRecentCommandsForCa(long caId) {
        return commandAuditService.findMostRecentUserCommandsForCa(caId);
    }

    @Override
    public List<ProvisioningAuditData> findMostRecentMessagesForCa(String caUUID) {
        return provisioningAuditLogService.findRecentMessagesForCA(caUUID);
    }

    @Override
    public Collection<CaStat> getCaStats() {
        return certificateAuthorityRepository.getCAStats();
    }

    @Override
    public Collection<CaStatEvent> getCaStatEvents() {
        return certificateAuthorityRepository.getCAStatEvents();
    }

    @Override
    public List<CertificateAuthorityData> findAllHostedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth() {
        Stream<HostedCertificateAuthority> certificateAuthorities = entityManager.createQuery(
                "SELECT DISTINCT ca " +
                    " FROM HostedCertificateAuthority ca JOIN ca.keyPairs kp" +
                    " WHERE kp.status = :pending",
                HostedCertificateAuthority.class)
            .setParameter("pending", KeyPairStatus.PENDING)
            .getResultStream();
        return certificateAuthorities
            .sorted(Comparator.comparingInt(ca -> {
                int depth = 0;
                for (ParentCertificateAuthority parent = ca.getParent(); parent != null; parent = parent.getParent()) {
                    depth++;
                }
                return depth;
            }))
            .map(HostedCertificateAuthority::toData)
            .collect(Collectors.toList());
    }

    private Class<? extends CertificateAuthority> resolveClassForType(CertificateAuthorityType type) {
        switch (type) {
        case ALL_RESOURCES:
            return AllResourcesCertificateAuthority.class;
        case ROOT:
            return ProductionCertificateAuthority.class;
        case HOSTED:
            return HostedCertificateAuthority.class;
        }
        throw new IllegalArgumentException("Unrecognised CertificateAuthorityType: " + type);
    }

    private CertificateAuthorityData convertToCaData(CertificateAuthority ca) {
        return ca == null ? null : ca.toData();
    }
}
