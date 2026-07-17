package net.ripe.rpki.services.impl.jpa;

import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.ripencc.support.persistence.DateTimePersistenceConverter;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.ripencc.support.persistence.X500PrincipalPersistenceConverter;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@SuppressWarnings("java:S1192")
@Repository
@Transactional
public class JpaResourceCertificateRepository extends JpaRepository<ResourceCertificate> implements ResourceCertificateRepository {

    @Override
    protected Class<ResourceCertificate> getEntityClass() {
        return ResourceCertificate.class;
    }

    @Override
    public OutgoingResourceCertificate findLatestOutgoingCertificate(PublicKey subjectPublicKey, KeyPairEntity signingKeyPair) {
        TypedQuery<OutgoingResourceCertificate> query = manager.createQuery(
                        "from OutgoingResourceCertificate rc " +
                                "where rc.status = :current " +
                                "and rc.signingKeyPair.id = :signingKeyPair " +
                                "and rc.encodedSubjectPublicKey = :subjectPublicKey",
                        OutgoingResourceCertificate.class)
                .setParameter("current", CertificateStatus.CURRENT)
                .setParameter("signingKeyPair", signingKeyPair.getId())
                .setParameter("subjectPublicKey", subjectPublicKey.getEncoded());
        return findUniqueResult(query);
    }

    @Override
    public int countNonExpiredOutgoingCertificates(PublicKey subjectPublicKey, KeyPairEntity signingKeyPair) {
        return manager.createQuery("""
                                SELECT COUNT(*) FROM OutgoingResourceCertificate rc
                                WHERE rc.status <> :expired
                                AND rc.signingKeyPair = :signingKeyPair
                                AND rc.encodedSubjectPublicKey = :subjectPublicKey""",
                        Number.class
                )
                .setParameter("expired", CertificateStatus.EXPIRED)
                .setParameter("signingKeyPair", signingKeyPair)
                .setParameter("subjectPublicKey", subjectPublicKey.getEncoded())
                .getSingleResult()
                .intValue();
    }

    @Override
    public Collection<OutgoingResourceCertificate> findAllBySigningKeyPair(KeyPairEntity signingKeyPair) {
        return manager
            .createQuery("from OutgoingResourceCertificate rc where rc.signingKeyPair.id = :signingKeyPair", OutgoingResourceCertificate.class)
            .setParameter("signingKeyPair", signingKeyPair.getId())
            .getResultList();
    }

    @Override
    public Optional<IncomingResourceCertificate> findIncomingResourceCertificateBySubjectKeyPair(KeyPairEntity subjectKeyPair) {
        try {
            return Optional.of(
                manager.createQuery("from IncomingResourceCertificate rc where rc.subjectKeyPair.id = :subjectKeyPair", IncomingResourceCertificate.class)
                    .setParameter("subjectKeyPair", subjectKeyPair.getId())
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public Collection<RevokedCertificateEntry> findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(KeyPairEntity signingKeyPair, DateTime now) {
        Validate.notNull(signingKeyPair, "signingKeyPair is required");
        return manager.createQuery("select new net.ripe.rpki.domain.RevokedCertificateEntry(rc.serial, rc.revocationTime) " +
                                "from OutgoingResourceCertificate rc " +
                                "where rc.status = :revoked " +
                                "and rc.signingKeyPair.id = :signingKeyPair " +
                                "and rc.validityPeriod.notValidAfter > :now",
                        RevokedCertificateEntry.class)
                .setParameter("revoked", CertificateStatus.REVOKED)
                .setParameter("now", now)
                .setParameter("signingKeyPair", signingKeyPair.getId())
                .getResultList();
    }

    @Override
    public ExpireOutgoingResourceCertificatesResult expireOutgoingResourceCertificates(DateTime now) {
        Object[] counts = (Object[]) createNativeQuery("""
            WITH expired_certificates AS (
                UPDATE resourcecertificate
                SET status = :expired, version = version + 1, updated_at = :now
                WHERE type = 'OUTGOING'
                AND validity_not_after < :now
                AND status <> :expired
                RETURNING id
            ),
            deleted_roas AS (
                DELETE FROM roaentity
                WHERE certificate_id IN (SELECT id FROM expired_certificates)
                RETURNING id
            ),
            deleted_aspas AS (
                DELETE FROM aspaentity
                WHERE certificate_id IN (SELECT id FROM expired_certificates)
                RETURNING id
            ),
            withdrawn_objects AS (
                UPDATE published_object po
                SET status = CASE status
                             WHEN :toBePublished THEN :withdrawn
                             WHEN :published THEN :toBeWithdrawn
                             END,
                    version = version + 1,
                    updated_at = :now
                WHERE po.validity_not_after < :now
                  AND po.status IN (:toBePublished, :published)
                RETURNING id
            )
            SELECT (SELECT COUNT(*) FROM expired_certificates) AS expired_certificate_count,
                   (SELECT COUNT(*) FROM deleted_roas) AS deleted_roa_count,
                   (SELECT COUNT(*) FROM deleted_aspas) AS deleted_aspa_count,
                   (SELECT COUNT(*) FROM withdrawn_objects) AS withdrawn_object_count
            """)
            .setParameter("now", new DateTimePersistenceConverter().convertToDatabaseColumn(now))
            .setParameter("expired", CertificateStatus.EXPIRED.name())
            .setParameter("toBePublished", PublicationStatus.TO_BE_PUBLISHED.name())
            .setParameter("published", PublicationStatus.PUBLISHED.name())
            .setParameter("toBeWithdrawn", PublicationStatus.TO_BE_WITHDRAWN.name())
            .setParameter("withdrawn", PublicationStatus.WITHDRAWN.name())
            .getSingleResult();
        return new ExpireOutgoingResourceCertificatesResult(
                (long)counts[0], (long)counts[1], (long)counts[2], (long)counts[3]);
    }

    @Override
    public int deleteExpiredOutgoingResourceCertificates(DateTime expirationTime) {
        Validate.isTrue(expirationTime.isBeforeNow(), "expiration time must be in the past");
        // an expired embedded outgoing resource certificate can still be referenced from a manifest
        // if we fail to publish and update the manifest entity before the nextUpdateTime or
        // if we don't remove manifests left behind by key pairs that have been revoked
        return createQuery("""
                DELETE FROM OutgoingResourceCertificate rc
                WHERE rc.status in (:expired) AND rc.validityPeriod.notValidAfter < :expirationTime
                AND NOT EXISTS (SELECT manifest.id FROM ManifestEntity manifest WHERE manifest.certificate = rc)
                AND NOT EXISTS (SELECT roa.id FROM RoaEntity roa WHERE roa.certificate = rc)
                AND NOT EXISTS (SELECT aspa.id FROM AspaEntity aspa WHERE aspa.certificate = rc)
                """)
                .setParameter("expired", CertificateStatus.EXPIRED)
                .setParameter("expirationTime", expirationTime)
                .executeUpdate();
    }

    @Override
    public Collection<OutgoingResourceCertificate> findCurrentCertificatesBySubjectPublicKey(PublicKey subjectPublicKey) {
        Validate.notNull(subjectPublicKey, "subjectPublicKey is required");
        return manager.createQuery("from OutgoingResourceCertificate rc " +
                        "where rc.encodedSubjectPublicKey = :encodedSubjectPublicKey " +
                        "AND rc.status = :current", OutgoingResourceCertificate.class)
                .setParameter("encodedSubjectPublicKey", subjectPublicKey.getEncoded())
                .setParameter("current", CertificateStatus.CURRENT)
                .getResultList();
    }

    @Override
    public boolean deleteOutgoingCertificatesForRevokedKeyPair(KeyPairEntity signingKeyPair) {
        Validate.notNull(signingKeyPair, "signingKeyPair is required");

        Query query = createQuery(
                "DELETE FROM OutgoingResourceCertificate rc " +
                 "WHERE rc.signingKeyPair.id = :signingKeyPairId")
                .setParameter("signingKeyPairId", signingKeyPair.getId());

        return query.executeUpdate() != 0;
    }

    @Override
    public boolean existsCurrentOutgoingCertificatesExceptForManifest(KeyPairEntity signingKeyPair) {
        // Self-signed certificates used in tests should be excluded
        return !manager.createQuery("""
                        SELECT 1
                          FROM OutgoingResourceCertificate rc
                         WHERE rc.signingKeyPair = :signingKeyPair
                           AND rc.status = :current
                           AND rc.subject <> rc.issuer
                           AND NOT EXISTS (FROM ManifestEntity mft WHERE mft.certificate = rc)
                        """)
                .setParameter("signingKeyPair", signingKeyPair)
                .setParameter("current", CertificateStatus.CURRENT)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @Override
    public ImmutableResourceSet findCurrentOutgoingChildCertificateResources(X500Principal caName) {
        // Recursively query all outgoing child certificates to determine the complete set of resources that
        // have been issued. The recursive step only needs to be taken when the issuing certificate has inherited
        // resources, otherwise we can just take the resources from the certificate directly (since they must
        // contain all the child certificate resources anyway).
        // Base case: all outgoing certificates issued by the children of the CA indicated by `caName`
        // Recursive step: add all outgoing certificates issued by children if the issuing certificate has inherited
        // resources.
        @SuppressWarnings("unchecked")
        Stream<Object> resultStream = manager.createNativeQuery("""
                        WITH RECURSIVE certificate (requesting_ca_id, resources, inherited) AS (
                          SELECT rc.requesting_ca_id, rc.resources, (rc.asn_inherited OR rc.ipv4_inherited OR rc.ipv6_inherited) as inherited
                            FROM resourcecertificate rc
                              JOIN published_object po on rc.published_object_id = po.id
                           WHERE rc.type = :outgoing
                             AND po.status <> :withdrawn
                             AND requesting_ca_id IN (SELECT child.id
                                                        FROM certificateauthority child
                                                          LEFT JOIN certificateauthority parent ON child.parent_id = parent.id
                                                       WHERE UPPER(parent.name) = UPPER(:name))
                        UNION ALL
                          SELECT rc.requesting_ca_id, rc.resources, (rc.asn_inherited OR rc.ipv4_inherited OR rc.ipv6_inherited) as inherited
                            FROM certificate issuing
                              JOIN keypair signing_keypair ON issuing.requesting_ca_id = signing_keypair.ca_id
                              JOIN resourcecertificate rc ON signing_keypair.id = rc.signing_keypair_id
                              JOIN published_object po on rc.published_object_id = po.id
                           WHERE rc.type = :outgoing
                             AND po.status <> :withdrawn
                             AND issuing.inherited = TRUE
                        )
                        SELECT resources FROM certificate WHERE LENGTH(resources) > 0;
                        """)
            .setParameter("outgoing", "OUTGOING")
            .setParameter("withdrawn", PublicationStatus.WITHDRAWN.name())
            .setParameter("name", new X500PrincipalPersistenceConverter().convertToDatabaseColumn(caName))
            .getResultStream();
        return resultStream
            .map(obj -> ImmutableResourceSet.parse((String) obj))
            .flatMap(ImmutableResourceSet::stream)
            .collect(ImmutableResourceSet.collector());
    }

    @Override
    public ImmutableResourceSet findCurrentOutgoingResourceCertificateResources(X500Principal caName) {
        return manager.createQuery(
                "SELECT rc.resourceExtension.resources " +
                    "  FROM ManagedCertificateAuthority ca JOIN ca.keyPairs kp," +
                    "       OutgoingResourceCertificate rc " +
                    " WHERE rc.status = :current " +
                    "   AND upper(ca.name) = upper(:name) " +
                    "   AND rc.signingKeyPair = kp",
                ImmutableResourceSet.class)
            .setParameter("current", CertificateStatus.CURRENT)
            .setParameter("name", caName.getName())
            .getResultStream()
            .flatMap(ImmutableResourceSet::stream)
            .collect(ImmutableResourceSet.collector());
    }
}
