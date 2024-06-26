package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.*;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.server.api.commands.*;
import net.ripe.rpki.server.api.dto.CaStat;
import net.ripe.rpki.server.api.dto.CaStatCaEvent;
import net.ripe.rpki.server.api.dto.CaStatEvent;
import net.ripe.rpki.server.api.dto.CaStatRoaEvent;
import net.ripe.rpki.server.api.dto.KeyPairStatus;
import net.ripe.rpki.util.JdbcDBComponent;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import javax.security.auth.x500.X500Principal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository(value = "jpaCertificateAuthorityRepository")
@SuppressWarnings("java:S1192")
public class JpaCertificateAuthorityRepository extends JpaRepository<CertificateAuthority> implements CertificateAuthorityRepository {

    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");

    @Override
    protected Class<CertificateAuthority> getEntityClass() {
        return CertificateAuthority.class;
    }

    @Override
    public CertificateAuthority get(Object id) throws EntityNotFoundException {
        CertificateAuthority result = super.get(id);
        if (!result.getId().equals(id)) {
            // Check for presence of Hibernate 4.1.9 bug. Sometimes the id of the proxy doesn't seem
            // to be initialized correctly, especially on faster machines. Hibernate 4.1.8 doesn't
            // seem to have this problem, but who knows... - Erik 2013-2-4.
            throw new AssertionError("returned object's id does not match. Got <" + result.getId() + "> expected <" + id + ">");
        }
        return result;
    }

    @Override
    public void add(CertificateAuthority entity) {
        super.add(entity);

        try {
            // Flush session to see if the new CA violates the unique name constraint
            manager.flush();
        } catch (PersistenceException e) {
            if (JdbcDBComponent.isUniqueConstraintViolation(e, "certificateauthority_name_key")) {
                throw new NameNotUniqueException(entity.getName());
            }
        }
    }

    @Override
    public CertificateAuthority findByName(X500Principal name) {
        return findByTypeAndName(CertificateAuthority.class, name);
    }

    @Override
    public ProductionCertificateAuthority findRootCAByName(X500Principal name) {
        return findByTypeAndName(ProductionCertificateAuthority.class, name);
    }

    @Override
    public AllResourcesCertificateAuthority findAllResourcesCAByName(X500Principal name) {
        return findByTypeAndName(AllResourcesCertificateAuthority.class, name);
    }

    @Override
    public <T extends CertificateAuthority> T findByTypeAndName(Class<T> type, X500Principal name) {
        Validate.notNull(type, "name is null");

        try {
            Query query = createQuery("from " + type.getSimpleName() + " ca where upper(:name) = upper(ca.name)");
            return type.cast(query.setParameter("name", name.getName()).getSingleResult());
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public <T extends CertificateAuthority> T findByTypeAndUuid(Class<T> type, UUID uuid, LockModeType lockModeType) {
        Validate.notNull(uuid, "uuid is null");
        try {
            Query query = createQuery("from " + type.getSimpleName() + " ca where uuid = :uuid")
                .setParameter("uuid", uuid)
                .setLockMode(lockModeType);
            return type.cast(query.getSingleResult());
        } catch (NoResultException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<CertificateAuthority> findAllByParent(ParentCertificateAuthority parent) {
        Validate.notNull(parent, "parent is null");
        Query query = createQuery("from CertificateAuthority ca where parent = :parent");
        return query.setParameter("parent", parent).getResultList();
    }

    @Override
    public ManagedCertificateAuthority findManagedCa(Long id) {
        try {
            Query query = createQuery("from ManagedCertificateAuthority ca where id = :id");
            return (ManagedCertificateAuthority) query.setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public NonHostedCertificateAuthority findNonHostedCa(Long id) {
        try {
            Query query = createQuery("from NonHostedCertificateAuthority ca where id = :id");
            return (NonHostedCertificateAuthority) query.setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<CaStat> getCAStats() {
        final Stream<Object[]> rowStream = manager.createNativeQuery("SELECT " +
                "ca.name, " +
                "count(rp.*), " +
                "ca.created_at " +
                "FROM certificateauthority ca " +
                "LEFT JOIN roaconfiguration r ON r.certificateauthority_id = ca.id " +
                "LEFT JOIN roaconfiguration_prefixes rp ON rp.roaconfiguration_id = r.id " +
                "WHERE ca.type NOT IN ('ALL_RESOURCES', 'ROOT') " +
                "GROUP BY ca.name, ca.created_at"
        ).getResultStream();

        return rowStream.map(row -> {
            String caName = toStr(row[0]);
            int roaCount = toInt(row[1]);
            Instant createdAt = (Instant) row[2];
            return new CaStat(caName, roaCount, ISO_DATE_FORMAT.print(new DateTime(createdAt.toEpochMilli())));
        }).toList();
    }

    @Override
    public Collection<CaStatEvent> getCAStatEvents() {
        // Two legacy command types that _may_ be present
        final String createRoaSpec = "CreateRoaSpecificationCommand";
        final String deleteRoaSpec = "DeleteRoaSpecificationCommand";

        // recall: prefix/suffix for Collectors.joining are not in between elements, so it can not be
        final var commandTypesSqlList = Stream.of(
                createRoaSpec, deleteRoaSpec,
                UpdateRoaConfigurationCommand.class.getSimpleName(),
                ActivateHostedCertificateAuthorityCommand.class.getSimpleName(),
                ActivateNonHostedCertificateAuthorityCommand.class.getSimpleName(),
                DeleteCertificateAuthorityCommand.class.getSimpleName(),
                DeleteNonHostedCertificateAuthorityCommand.class.getSimpleName()
            ).map(s -> "'" + s + "'").collect(Collectors.joining(","));

        final Query q = manager.createNativeQuery("SELECT " +
                "ca.name, " +
                "au.commandtype, " +
                "au.executiontime, " +
                "au.commandsummary " +
                "FROM commandAudit au " +
                "LEFT JOIN certificateAuthority ca ON ca.id = au.ca_id " +
                "WHERE commandtype IN (" + commandTypesSqlList + ") " +
                "ORDER BY au.executiontime ASC, ca.name");

        final Pattern updateConfPattern = Pattern.compile("Updated ROA configuration. Additions: (.+). Deletions: (.+)\\.");
        final Pattern createSpecPattern = Pattern.compile("Created ROA specification '.+' (.+).");
        final Pattern deleteSpecPattern = Pattern.compile("Deleted ROA specification '.+' (.+).");

        final List<?> resultList = q.getResultList();
        final List<CaStatEvent> result = new ArrayList<>();
        for (final Object r : resultList) {
            final Object[] columns = (Object[]) r;
            final String caName = toStr(columns[0]);
            final String type = toStr(columns[1]);
            final String date = ISO_DATE_FORMAT.print(new DateTime(((Instant)columns[2]).toEpochMilli()));
            final String summary = toStr(columns[3]);

            if (UpdateRoaConfigurationCommand.class.getSimpleName().equals(type)) {
                final Matcher m = updateConfPattern.matcher(summary);
                if (m.matches()) {
                    final String additions = m.group(1);
                    final String deletions = m.group(2);
                    result.add(new CaStatRoaEvent(caName, date, countRoas(additions), countRoas(deletions)));
                }
            } else if (createRoaSpec.equals(type)) {
                final Matcher m = createSpecPattern.matcher(summary);
                if (m.matches()) {
                    final String additions = m.group(1);
                    int added = countRoasUpdateSpecPattern(additions);
                    if (added > 0)
                        result.add(new CaStatRoaEvent(caName, date, added, 0));
                }
            } else if (deleteRoaSpec.equals(type)) {
                final Matcher m = deleteSpecPattern.matcher(summary);
                if (m.matches()) {
                    final String deletions = m.group(1);
                    int deleted = countRoasUpdateSpecPattern(deletions);
                    if (deleted > 0)
                        result.add(new CaStatRoaEvent(caName, date, 0, deleted));
                }
            } else if (ActivateHostedCertificateAuthorityCommand.class.getSimpleName().equals(type) || ActivateNonHostedCertificateAuthorityCommand.class.getSimpleName().equals(type)) {
                result.add(CaStatCaEvent.created(caName, date));
            } else if (DeleteCertificateAuthorityCommand.class.getSimpleName().equals(type) || DeleteNonHostedCertificateAuthorityCommand.class.getSimpleName().equals(type)) {
                result.add(CaStatCaEvent.deleted(date));
            }
        }
        return result;
    }

    @Override
    public Collection<ManagedCertificateAuthority> findAllWithOutdatedManifests(boolean includeUpdatedConfiguration, DateTime nextUpdateCutoff, int maxResults) {
        return manager.createQuery(
            "SELECT ca" +
                "  FROM " + ManagedCertificateAuthority.class.getSimpleName() + " ca" +
                // Certificate authority configuration was updated since the last time it as applied, so publish might be needed
                " WHERE (:includeUpdatedConfiguration = TRUE AND ca.configurationUpdatedAt > ca.configurationAppliedAt)" +
                "    OR EXISTS (SELECT kp" +
                "                 FROM ca.keyPairs kp" +
                "                 JOIN kp.incomingResourceCertificate incoming" +
                // Key pair must be publishable and must have a current incoming certificate
                "                WHERE kp.status IN (:publishable)" +
                // Objects that need to be withdrawn or published
                "                  AND (       EXISTS (SELECT po" +
                "                                        FROM PublishedObject po" +
                "                                       WHERE po.issuingKeyPair = kp" +
                "                                         AND po.status in :pending)" +
                // No active manifest, or manifest will expire soon, so publish needed
                "                       OR NOT EXISTS (SELECT mft" +
                "                                        FROM ManifestEntity mft" +
                "                                        JOIN mft.publishedObject po" +
                "                                       WHERE mft.keyPair = kp" +
                "                                         AND po.status IN :active" +
                "                                         AND po.validityPeriod.notValidAfter > :nextUpdateCutoff)" +
                // No active CRL, or CRL will expire soon, so publish needed
                "                       OR NOT EXISTS (SELECT crl" +
                "                                        FROM CrlEntity crl" +
                "                                        JOIN crl.publishedObject po" +
                "                                       WHERE crl.keyPair = kp" +
                "                                         AND po.status IN :active" +
                "                                         AND po.validityPeriod.notValidAfter > :nextUpdateCutoff)))",
            ManagedCertificateAuthority.class)
            // See KeyPairEntity.isPublishable for the next two parameters
            .setParameter("publishable", Arrays.asList(KeyPairStatus.PENDING, KeyPairStatus.CURRENT, KeyPairStatus.OLD))
            // Need to update when there are published object with pending status
            .setParameter("active", PublicationStatus.ACTIVE_STATUSES)
            .setParameter("pending", PublicationStatus.PENDING_STATUSES)
            .setParameter("nextUpdateCutoff", nextUpdateCutoff)
            .setParameter("includeUpdatedConfiguration", includeUpdatedConfiguration)
            .setMaxResults(maxResults)
            .getResultList();
    }

    @Override
    public List<ManagedCertificateAuthority> findAllWithManifestsExpiringBefore(DateTime notValidAfterCutoff, int maxResult) {
        return manager.createQuery(
                        "SELECT DISTINCT ca, MIN(po.validityPeriod.notValidAfter) " +
                                "  FROM ManagedCertificateAuthority ca" +
                                "  JOIN ca.keyPairs kp," +
                                "       ManifestEntity mft" +
                                "  JOIN mft.publishedObject po" +
                                "  JOIN mft.certificate crt" +
                                " WHERE kp.status IN :publishable" +
                                "   AND crt.signingKeyPair = kp" +
                                "   AND po.validityPeriod.notValidAfter < :notValidAfterCutoff" +
                                " GROUP BY ca" +
                                " ORDER BY MIN(po.validityPeriod.notValidAfter) ASC",
                        Object[].class)
                // See KeyPairEntity.isPublishable for the next two parameters
                .setParameter("publishable", Arrays.asList(KeyPairStatus.PENDING, KeyPairStatus.CURRENT, KeyPairStatus.OLD))
                .setParameter("notValidAfterCutoff", notValidAfterCutoff)
                .setMaxResults(maxResult)
                .getResultStream()
                .map(row -> (ManagedCertificateAuthority) row[0]).toList();
    }

    @Override
    public int deleteNonHostedPublicKeysWithoutSigningCertificates() {
        return createQuery("DELETE FROM PublicKeyEntity pk WHERE pk.outgoingResourceCertificates IS EMPTY")
            .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<ManagedCertificateAuthority> getCasWithoutKeyPairsAndRoaConfigurationsAndUserActivityDuringTheLastYear() {
        // for context: deleting a CA is a USER command
        final Query sql = manager.createQuery(
            "SELECT ca FROM ManagedCertificateAuthority ca " +
                "WHERE ca.keyPairs IS EMPTY " +
                "AND NOT EXISTS (" +
                "   SELECT cau FROM CommandAudit cau " +
                "   WHERE cau.certificateAuthorityId = ca.id" +
                "   AND cau.commandGroup = :user " +
                "   AND cau.executionTime > :threshold " +
                ") " +
                "AND NOT EXISTS (" +
                "   SELECT rc FROM RoaConfiguration rc " +
                "   WHERE rc.certificateAuthority = ca " +
                "   AND rc.prefixes IS NOT EMPTY" +
                ") " +
                "AND NOT EXISTS (" +
                "   SELECT rac FROM RoaAlertConfiguration rac " +
                "   WHERE rac.certificateAuthority = ca" +
                ")");
        final DateTime yearAgo = new DateTime(DateTimeZone.UTC).minus(Duration.standardDays(366));
        return sql
            .setParameter("threshold", yearAgo)
            .setParameter("user", CertificateAuthorityCommandGroup.USER)
            .getResultList();
    }

    @Override
    public Optional<IntermediateCertificateAuthority> findSmallestIntermediateCA(X500Principal productionCaName) {
        try {
            return Optional.of(manager.createQuery(
                        "SELECT ca FROM IntermediateCertificateAuthority ca LEFT JOIN CertificateAuthority child ON ca = child.parent" +
                            " WHERE ca.parent.name = :productionCaName" +
                            " GROUP BY ca " +
                            " ORDER BY count(child) ASC, RANDOM()",
                        IntermediateCertificateAuthority.class
                    )
                    .setParameter("productionCaName", productionCaName)
                    .setMaxResults(1)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private static int countRoasUpdateSpecPattern(String summary) {
        int c = summary.replaceFirst("\\[asn=AS[0-9]+, ", "").split("maximumLength=").length - 1;
        return Math.max(c, 0);
    }

    private static int countRoas(String summaryArray) {
        if (summaryArray != null && !"none".equals(summaryArray)) {
            return summaryArray.split("\\], \\[").length;
        }
        return 0;
    }

    private static String toStr(Object o) {
        return o == null ? null : o.toString();
    }

    private static int toInt(Object o) {
        return o == null ? 0 : Integer.parseInt(o.toString());
    }
}
