package net.ripe.rpki.services.impl.jpa;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.domain.IncomingResourceCertificate;
import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.OutgoingResourceCertificate;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.ResourceCertificate;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.ripencc.support.persistence.DateTimePersistenceConverter;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.server.api.dto.OutgoingResourceCertificateStatus;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
        Query query = createQuery("from OutgoingResourceCertificate rc where rc.status = :current and rc.signingKeyPair.id = :signingKeyPair and rc.encodedSubjectPublicKey = :subjectPublicKey ");
        query.setParameter("current", OutgoingResourceCertificateStatus.CURRENT);
        query.setParameter("signingKeyPair", signingKeyPair.getId());
        query.setParameter("subjectPublicKey", subjectPublicKey.getEncoded());
        return (OutgoingResourceCertificate) findUniqueResult(query);
    }

    @Override
    public int countNonExpiredOutgoingCertificates(PublicKey subjectPublicKey, KeyPairEntity signingKeyPair) {
        Object count = createQuery("SELECT COUNT(*) FROM OutgoingResourceCertificate rc WHERE rc.status <> :expired AND rc.signingKeyPair = :signingKeyPair AND rc.encodedSubjectPublicKey = :subjectPublicKey")
            .setParameter("expired", OutgoingResourceCertificateStatus.EXPIRED)
            .setParameter("signingKeyPair", signingKeyPair)
            .setParameter("subjectPublicKey", subjectPublicKey.getEncoded())
            .getSingleResult();
        return ((Number) count).intValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<OutgoingResourceCertificate> findAllBySigningKeyPair(KeyPairEntity signingKeyPair) {
        Query query = createQuery("from OutgoingResourceCertificate rc where rc.signingKeyPair.id = :signingKeyPair");
        query.setParameter("signingKeyPair", signingKeyPair.getId());
        return (List<OutgoingResourceCertificate>) query.getResultList();
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    @Override
    public Collection<OutgoingResourceCertificate> findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(KeyPairEntity signingKeyPair, DateTime now) {
        Validate.notNull(signingKeyPair, "signingKeyPair is required");
        Query query = createQuery("from OutgoingResourceCertificate rc " +
                "where rc.status = :revoked " +
                "and rc.signingKeyPair.id = :signingKeyPair " +
                "and rc.validityPeriod.notValidAfter > :now");
        query.setParameter("revoked", OutgoingResourceCertificateStatus.REVOKED);
        query.setParameter("now", now);
        query.setParameter("signingKeyPair", signingKeyPair.getId());
        return query.getResultList();
    }

    @Override
    public ExpireOutgoingResourceCertificatesResult expireOutgoingResourceCertificates(DateTime now) {
        Object[] counts = (Object[]) createNativeQuery("WITH expired_certificates AS (\n" +
            "    UPDATE resourcecertificate\n" +
            "    SET status = :expired, version = version + 1, updated_at = :now\n" +
            "    WHERE type = 'OUTGOING' AND validity_not_after < :now AND status <> :expired\n" +
            "    RETURNING id\n" +
            "),\n" +
            "deleted_roas AS (\n" +
            "    DELETE FROM roaentity\n" +
            "    WHERE EXISTS (SELECT id FROM expired_certificates WHERE expired_certificates.id = roaentity.certificate_id)\n" +
            "    RETURNING id\n" +
            "),\n" +
            "withdrawn_objects AS (\n" +
            "    UPDATE published_object po\n" +
            "    SET status = CASE status\n" +
            "                 WHEN :toBePublished THEN :withdrawn\n" +
            "                 WHEN :published THEN :toBeWithdrawn\n" +
            "                 END,\n" +
            "        version = version + 1,\n" +
            "        updated_at = :now\n" +
            "    WHERE po.validity_not_after < :now\n" +
            "      AND po.status IN (:toBePublished, :published)\n" +
            "    RETURNING id\n" +
            ")\n" +
            "SELECT (SELECT COUNT(*) FROM expired_certificates) AS expired_certificate_count,\n" +
            "       (SELECT COUNT(*) FROM deleted_roas) AS deleted_roa_count,\n" +
            "       (SELECT COUNT(*) FROM withdrawn_objects) AS withdrawn_object_count\n")
            .setParameter("now", new DateTimePersistenceConverter().convertToDatabaseColumn(now))
            .setParameter("expired", OutgoingResourceCertificateStatus.EXPIRED.name())
            .setParameter("toBePublished", PublicationStatus.TO_BE_PUBLISHED.name())
            .setParameter("published", PublicationStatus.PUBLISHED.name())
            .setParameter("toBeWithdrawn", PublicationStatus.TO_BE_WITHDRAWN.name())
            .setParameter("withdrawn", PublicationStatus.WITHDRAWN.name())
            .getSingleResult();
        return new ExpireOutgoingResourceCertificatesResult(
            ((BigInteger) counts[0]).intValueExact(),
            ((BigInteger) counts[1]).intValueExact(),
            ((BigInteger) counts[2]).intValueExact()
        );
    }

    @Override
    public int deleteExpiredOutgoingResourceCertificates(DateTime expirationTime) {
        Validate.isTrue(expirationTime.isBeforeNow(), "expiration time must be in the past");
        return createQuery("DELETE FROM OutgoingResourceCertificate rc " +
            "WHERE rc.status in (:expired) AND rc.validityPeriod.notValidAfter < :expirationTime " +
            // an expired embedded outgoing resource certificate can still be referenced from a manifest
            // if we fail to publish and update the manifest entity before the nextUpdateTime or
            // if we don't remove manifests left behind by key pairs that have been revoked
            "AND NOT EXISTS (SELECT manifest.id FROM ManifestEntity manifest WHERE manifest.certificate = rc) " +
            // FIXME: there are expired embedded outgoing certificates referenced from ROAs in the database...
            "AND NOT EXISTS (SELECT roa.id FROM RoaEntity roa WHERE roa.certificate = rc)")
                .setParameter("expired", OutgoingResourceCertificateStatus.EXPIRED)
                .setParameter("expirationTime", expirationTime)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<OutgoingResourceCertificate> findCurrentCertificatesBySubjectPublicKey(PublicKey subjectPublicKey) {
        Validate.notNull(subjectPublicKey, "subjectPublicKey is required");
        Query query = createQuery("from OutgoingResourceCertificate rc where rc.encodedSubjectPublicKey = :encodedSubjectPublicKey AND rc.status = :current");
        query.setParameter("encodedSubjectPublicKey", subjectPublicKey.getEncoded());
        query.setParameter("current", OutgoingResourceCertificateStatus.CURRENT);
        return query.getResultList();
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
        return !manager.createQuery("SELECT 1 " +
                "  FROM OutgoingResourceCertificate rc " +
                " WHERE rc.signingKeyPair = :signingKeyPair " +
                "   AND rc.status = :current " +
                "   AND rc.subject <> rc.issuer " + // Self signed certificates used in tests should be excluded
                "   AND NOT EXISTS (FROM ManifestEntity mft WHERE mft.certificate = rc)")
            .setParameter("signingKeyPair", signingKeyPair)
            .setParameter("current", OutgoingResourceCertificateStatus.CURRENT)
            .setMaxResults(1)
            .getResultList()
            .isEmpty();
    }

    @Override
    public IpResourceSet findCurrentOutgoingChildCertificateResources(X500Principal caName) {
        return manager.createQuery(
                "SELECT rc.resources " +
                    "  FROM OutgoingResourceCertificate rc INNER JOIN rc.requestingCertificateAuthority child " +
                    " WHERE rc.status = :current " +
                    "   AND upper(child.parent.name) = upper(:name)",
                IpResourceSet.class)
            .setParameter("current", OutgoingResourceCertificateStatus.CURRENT)
            .setParameter("name", caName.getName())
            .getResultStream()
            .collect(IpResourceSet::new, IpResourceSet::addAll, IpResourceSet::addAll);
    }

    @Override
    public IpResourceSet findCurrentOutgoingRpkiObjectCertificateResources(X500Principal caName) {
        return manager.createQuery(
                "SELECT rc.resources " +
                    "  FROM HostedCertificateAuthority ca JOIN ca.keyPairs kp," +
                    "       OutgoingResourceCertificate rc " +
                    " WHERE rc.status = :current " +
                    "   AND upper(ca.name) = upper(:name) " +
                    "   AND rc.requestingCertificateAuthority IS NULL " +
                    "   AND rc.signingKeyPair = kp",
                IpResourceSet.class)
            .setParameter("current", OutgoingResourceCertificateStatus.CURRENT)
            .setParameter("name", caName.getName())
            .getResultStream()
            .collect(IpResourceSet::new, IpResourceSet::addAll, IpResourceSet::addAll);
    }
}
