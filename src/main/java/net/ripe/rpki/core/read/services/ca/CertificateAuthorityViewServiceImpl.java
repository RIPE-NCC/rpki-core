package net.ripe.rpki.core.read.services.ca;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.domain.CertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.NonHostedPublisherRepository;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.ripencc.provisioning.ProvisioningAuditLogService;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.joda.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import javax.persistence.TypedQuery;
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
    public CertificateAuthorityData findCertificateAuthorityByTypeAndUuid(Class<? extends CertificateAuthority> type, UUID uuid) {
        CertificateAuthority ca = certificateAuthorityRepository.findByTypeAndUuid(type, uuid, LockModeType.NONE);
        return convertToCaData(ca);
    }

    @Override
    public Long findCertificateAuthorityIdByTypeAndName(Class<? extends CertificateAuthority> type, X500Principal name) {
        CertificateAuthority ca = certificateAuthorityRepository.findByTypeAndName(type, name);
        return ca == null ? null : ca.getVersionedId().getId();
    }

    @Override
    public CertificateAuthorityData findCertificateAuthority(Long caId) {
        CertificateAuthority ca = certificateAuthorityRepository.findManagedCa(caId);
        return convertToCaData(ca);
    }

    @Override
    public Collection<CertificateAuthorityData> findAllChildrenForCa(X500Principal caName) {
        ManagedCertificateAuthority parent = certificateAuthorityRepository.findByTypeAndName(ManagedCertificateAuthority.class, caName);
        return certificateAuthorityRepository.findAllByParent(parent).stream()
                .map(this::convertToCaData)
                .collect(Collectors.toList());
    }

    // Optimized version of the above to avoid all the resources heavy-lifting
    // when it's not needed.
    @Override
    public Collection<CaIdentity> findAllChildrenIdsForCa(X500Principal productionCaName) {
        ManagedCertificateAuthority parent = certificateAuthorityRepository.findByTypeAndName(ProductionCertificateAuthority.class, productionCaName);
        return certificateAuthorityRepository.findAllByParent(parent).stream()
            .map(ca -> new CaIdentity(ca.getVersionedId(), CaName.of(ca.getName())))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<CertificateAuthorityData> findAllHostedCertificateAuthorities() {
        return findCertificateAuthoritiesMatchingType(CertificateAuthorityType.ROOT, CertificateAuthorityType.HOSTED, CertificateAuthorityType.ALL_RESOURCES);
    }

    @Override
    public Collection<CertificateAuthorityData> findAllHostedCasWithCurrentKeyOnlyAndOlderThan(
        Class<? extends ManagedCertificateAuthority> type,
        final Instant oldestCreationTime,
        final Optional<Integer> batchSize
    ) {
        final TypedQuery<ManagedCertificateAuthority> query = entityManager.createQuery(
            "SELECT ca " +
                " FROM " + type.getSimpleName() + " ca " +
                " WHERE EXISTS (SELECT kp FROM ca.keyPairs kp" +
                "                WHERE kp.status = :current " +
                "                  AND kp.createdAt < :maxAge)" +
                " AND NOT EXISTS (SELECT kp FROM ca.keyPairs kp WHERE kp.status <> :current)",
            ManagedCertificateAuthority.class)
            .setParameter("current", KeyPairStatus.CURRENT)
            .setParameter("maxAge", oldestCreationTime);
        batchSize.ifPresent(query::setMaxResults);
        return query.getResultStream()
            .map(ManagedCertificateAuthority::toData)
            .collect(Collectors.toList());
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
        return commandAuditService.findMostRecentCommandsForCa(caId);
    }

    @Override
    public List<ProvisioningAuditData> findMostRecentMessagesForCa(UUID caUUID) {
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
    public Map<UUID, RepositoryResponse> findNonHostedPublisherRepositories(X500Principal caName) {
        NonHostedCertificateAuthority ca = certificateAuthorityRepository.findByTypeAndName(NonHostedCertificateAuthority.class, caName);
        if (ca == null) {
            throw new EntityNotFoundException("non-hosted CA '" + caName + "' not found");
        }

        return ca.getPublisherRepositories().stream().collect(Collectors.toMap(
            NonHostedPublisherRepository::getPublisherHandle,
            NonHostedPublisherRepository::getRepositoryResponse
        ));
    }

    @Override
    public Map<UUID, PublisherRequest> findAllPublisherRequestsFromNonHostedCAs() {
        return entityManager.createQuery("from NonHostedPublisherRepository repository", NonHostedPublisherRepository.class).getResultStream()
                .collect(Collectors.toMap(NonHostedPublisherRepository::getPublisherHandle, NonHostedPublisherRepository::getPublisherRequest));
    }

    @Override
    public List<CertificateAuthorityData> findAllManagedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth() {
        Stream<ManagedCertificateAuthority> certificateAuthorities = entityManager.createQuery(
                "SELECT DISTINCT ca " +
                    " FROM ManagedCertificateAuthority ca JOIN ca.keyPairs kp" +
                    " WHERE kp.status = :pending",
                ManagedCertificateAuthority.class)
            .setParameter("pending", KeyPairStatus.PENDING)
            .getResultStream();
        return certificateAuthorities
            .sorted(Comparator.comparingInt(CertificateAuthority::depth))
            .map(ManagedCertificateAuthority::toData)
            .collect(Collectors.toList());
    }

    private CertificateAuthorityData convertToCaData(CertificateAuthority ca) {
        return ca == null ? null : ca.toData();
    }
}
