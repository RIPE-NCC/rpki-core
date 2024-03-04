package net.ripe.rpki.core.read.services.ca;

import net.ripe.rpki.commons.provisioning.identity.PublisherRequest;
import net.ripe.rpki.commons.provisioning.identity.RepositoryResponse;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.audit.CommandAuditService;
import net.ripe.rpki.ripencc.provisioning.ProvisioningAuditLogService;
import net.ripe.rpki.server.api.dto.*;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.apache.commons.lang3.tuple.Pair;
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
        CertificateAuthority parent = certificateAuthorityRepository.findByTypeAndName(CertificateAuthority.class, caName);
        return parent instanceof ParentCertificateAuthority
            ? certificateAuthorityRepository.findAllByParent((ParentCertificateAuthority) parent).stream()
                .map(this::convertToCaData)
                .collect(Collectors.toList())
            : Collections.emptyList();
    }

    @Override
    public Optional<CertificateAuthorityData> findSmallestIntermediateCa(X500Principal productionCaName) {
        return certificateAuthorityRepository.findSmallestIntermediateCA(productionCaName).map(CertificateAuthority::toData);
    }

    @Override
    public Collection<ManagedCertificateAuthorityData> findManagedCasEligibleForKeyRevocation() {
        return entityManager.createQuery(
                "FROM ManagedCertificateAuthority ca " +
                    "WHERE EXISTS (FROM ca.keyPairs kp WHERE kp.status = :old)",
                ManagedCertificateAuthority.class
            )
            .setParameter("old", KeyPairStatus.OLD)
            .getResultStream()
            .map(ManagedCertificateAuthority::toData)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<ManagedCertificateAuthorityData> findManagedCasEligibleForKeyRoll(
        Class<? extends ManagedCertificateAuthority> type,
        final Instant oldestKpCreationTime,
        final Optional<Integer> batchSize
    ) {
        final TypedQuery<ManagedCertificateAuthority> query = entityManager.createQuery(
            "SELECT ca " +
                " FROM " + type.getSimpleName() + " ca " +
                " WHERE " +
                "( " +
                "EXISTS (SELECT kp FROM ca.keyPairs kp" +
                "                WHERE kp.status = :current " +
                "                  AND kp.createdAt < :maxKpAge)" +
                "AND NOT EXISTS (SELECT kp FROM ca.keyPairs kp WHERE kp.status <> :current)" +
                ") OR (" +
                "NOT EXISTS (SELECT kp FROM ca.keyPairs kp)" +
                ")",
            ManagedCertificateAuthority.class)
            .setParameter("current", KeyPairStatus.CURRENT)
            .setParameter("maxKpAge", oldestKpCreationTime);
        batchSize.ifPresent(query::setMaxResults);
        return query.getResultStream()
            .map(ManagedCertificateAuthority::toData)
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
    public Map<UUID, Pair<PublisherRequest, RepositoryResponse>> findNonHostedPublisherRepositories(X500Principal caName) {
        NonHostedCertificateAuthority ca = certificateAuthorityRepository.findByTypeAndName(NonHostedCertificateAuthority.class, caName);
        if (ca == null) {
            throw new EntityNotFoundException("non-hosted CA '" + caName + "' not found");
        }

        return ca.getPublisherRepositories().values().stream().collect(Collectors.toMap(
            NonHostedPublisherRepository::getPublisherHandle,
            repository -> Pair.of(repository.getPublisherRequest(), repository.getRepositoryResponse())
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
