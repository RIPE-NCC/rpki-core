package net.ripe.rpki.core.read.services.ca;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.util.KeyPairUtil;
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
        if (parent instanceof ParentCertificateAuthority parentCa) {
            return certificateAuthorityRepository.findAllByParent(parentCa)
                .stream().map(this::convertToCaData).toList();
        }
        return List.of();
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
                .map(ManagedCertificateAuthority::toData).toList();
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
                .map(ManagedCertificateAuthority::toData).toList();
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
            .map((x) -> (CertificateAuthorityData) x.toData())
            .toList();
    }

    private CertificateAuthorityData convertToCaData(CertificateAuthority ca) {
        return ca == null ? null : ca.toData();
    }

    @Override
    public List<DelegatedCa> findDelegatedCas() {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(
            """
                SELECT
                    ca.name,
                    (SELECT pk.encoded
                            FROM non_hosted_ca_public_key pk
                            WHERE pk.ca_id = ca.id
                            ORDER BY pk.id DESC
                            LIMIT 1) AS last_public_key_encoded,
                    stat.last_success AS last_succeeded_at,
                    stat.last_failure AS last_failed_at,
                    stat.last_failure_reason AS error_reason
                FROM certificateauthority ca
                  LEFT JOIN provisioning_stat stat ON stat.non_hosted_ca_uuid = ca.uuid
                WHERE ca.type = 'NONHOSTED'
                ORDER BY ca.name
            """)
            .getResultList();

        var hexFormat = HexFormat.of();
        return results.stream().map(row -> {
            var caName            = (String)            row[0];
            var encodedPublicKey  = (byte[])            row[1];
            var lastProvisionedAt = (java.time.Instant) row[2];
            var lastFailedAt      = (java.time.Instant) row[3];
            var errorReason       = (String)            row[4];

            Optional<String> lastPublicKey = Optional.ofNullable(encodedPublicKey)
                    .map(encoded -> hexFormat.formatHex(KeyPairUtil.getKeyIdentifier(KeyPairFactory.decodePublicKey(encoded))));

            return new DelegatedCa(
                    caName,
                    lastPublicKey,
                    Optional.ofNullable(lastProvisionedAt),
                    Optional.ofNullable(lastFailedAt),
                    Optional.ofNullable(errorReason)
            );
        }).toList();
    }
}
