package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.RevokedCertificateEntry;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificate;
import net.ripe.rpki.domain.bgpsec.BgpSecCertificateRepository;
import net.ripe.rpki.ripencc.support.persistence.DateTimePersistenceConverter;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import net.ripe.rpki.server.api.dto.CertificateStatus;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Repository
@Transactional
public class JpaBgpSecCertificateRepository extends JpaRepository<BgpSecCertificate> implements BgpSecCertificateRepository {

    @Override
    protected Class<BgpSecCertificate> getEntityClass() {
        return BgpSecCertificate.class;
    }

    @Override
    public ExpireBgpSecCertificatesResult expireOutdatedBgpSecCertificates(DateTime now) {
        Object[] counts = (Object[]) createNativeQuery("""
                WITH expired_certificates AS (
                    UPDATE bgpsec_certificate
                    SET status = :expired, version = version + 1, updated_at = :now
                    WHERE validity_not_after < :now
                    AND status <> :expired
                    RETURNING id
                ),
                deleted_bgpsecs AS (
                    DELETE FROM bgpsec_entity
                    WHERE certificate_id IN (SELECT id FROM expired_certificates)
                    RETURNING published_object_id
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
                      AND po.id IN (SELECT published_object_id FROM deleted_bgpsecs)
                    RETURNING id
                )
                SELECT (SELECT COUNT(*) FROM expired_certificates) AS expired_certificate_count,
                       (SELECT COUNT(*) FROM deleted_bgpsecs) AS deleted_bgpsec_count,
                       (SELECT COUNT(*) FROM withdrawn_objects) AS withdrawn_object_count
                """)
                .setParameter("now", new DateTimePersistenceConverter().convertToDatabaseColumn(now))
                .setParameter("expired", CertificateStatus.EXPIRED.name())
                .setParameter("toBePublished", PublicationStatus.TO_BE_PUBLISHED.name())
                .setParameter("published", PublicationStatus.PUBLISHED.name())
                .setParameter("toBeWithdrawn", PublicationStatus.TO_BE_WITHDRAWN.name())
                .setParameter("withdrawn", PublicationStatus.WITHDRAWN.name())
                .getSingleResult();

        return new ExpireBgpSecCertificatesResult((long) counts[0], (long) counts[1], (long) counts[2]);
    }

    @Override
    public int deleteExpiredBgpSecCertificates(DateTime expirationTime) {
        Validate.isTrue(expirationTime.isBeforeNow(), "expirationTime must be in the past");
        return createQuery("""
                DELETE FROM BgpSecCertificate c
                WHERE c.status = :expired
                AND c.validityPeriod.notValidAfter < :expirationTime
                AND NOT EXISTS (SELECT be.id FROM BgpSecEntity be WHERE be.certificate = c)
                """)
                .setParameter("expired", CertificateStatus.EXPIRED)
                .setParameter("expirationTime", expirationTime)
                .executeUpdate();
    }

    @Override
    public Collection<RevokedCertificateEntry> findRevokedCertificatesWithValidityTimeAfterNowBySigningKeyPair(KeyPairEntity signingKeyPair, DateTime now) {
        Validate.notNull(signingKeyPair, "signingKeyPair is required");
        return manager.createQuery("select new net.ripe.rpki.domain.RevokedCertificateEntry(c.serial, c.revocationTime) " +
                                "from BgpSecCertificate c " +
                                "where c.status = :revoked " +
                                "and c.signingKeyPair.id = :signingKeyPair " +
                                "and c.validityPeriod.notValidAfter > :now",
                        RevokedCertificateEntry.class)
                .setParameter("revoked", CertificateStatus.REVOKED)
                .setParameter("now", now)
                .setParameter("signingKeyPair", signingKeyPair.getId())
                .getResultList();
    }
}

