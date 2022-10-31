package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.KeyPairEntity;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.PublishedObject;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.domain.PublishedObjectEntry;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class JpaPublishedObjectRepository extends JpaRepository<PublishedObject> implements PublishedObjectRepository {

    @Override
    public List<PublishedObject> findActiveManifestEntries(KeyPairEntity keyPair) {
        return manager.createQuery("select po from PublishedObject po " +
                "where po.issuingKeyPair.id = :keyPair " +
                "and po.status in :active " +
                "and po.includedInManifest = true " +
                "order by po.id asc",
            PublishedObject.class)
            .setParameter("keyPair", keyPair.getId())
            .setParameter("active", PublicationStatus.ACTIVE_STATUSES)
            .getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<PublishedObjectEntry> findEntriesByPublicationStatus(EnumSet<PublicationStatus> statuses) {
        return manager.createNativeQuery("SELECT " +
                "updated_at, status, directory || filename as uri, sha256(content) as sha256_content " +
                "FROM published_object po " +
                "WHERE po.status IN :statuses " +
                "UNION ALL " +
                "SELECT " +
                "updated_at, status, uri, sha256(content) as sha256_content " +
                "FROM ta_published_object tap " +
                "WHERE tap.status IN :statuses ",
            "PublishedObjectEntryResult")
            .setParameter("statuses", statuses.stream().map(PublicationStatus::name).collect(Collectors.toSet()))
            .getResultList();
    }

    @Override
    public List<PublishedObjectData> findCurrentlyPublishedObjects() {
        @SuppressWarnings("unchecked")
        Stream<PublishedObjectData> stream = manager.createNativeQuery(
            "SELECT po.created_at, po.directory || po.filename AS uri, po.content " +
                "  FROM published_object po " +
                " WHERE po.status IN :published " +
                "UNION ALL " +
                "SELECT po.created_at, po.uri, po.content " +
                "  FROM ta_published_object po " +
                " WHERE po.status IN :published ")
            .setParameter("published", PublicationStatus.PUBLISHED_STATUSES.stream().map(PublicationStatus::name).collect(Collectors.toSet()))
            .getResultStream()
            .map((o) -> {
                Object[] row = (Object[]) o;
                return new PublishedObjectData((Timestamp) row[0], URI.create((String) row[1]), (byte[]) row[2]);
            });

        return stream.collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Long> findObjectIdsWithoutValidityPeriod() {
        return createQuery("select id from PublishedObject po where po.validityPeriod is null")
            .getResultList();
    }

    @Override
    public void removeAll(List<PublishedObject> publishedObjects) {
        if (publishedObjects == null || publishedObjects.isEmpty()) {
            return;
        }
        for (PublishedObject publishedObject : publishedObjects) {
            manager.remove(manager.merge(publishedObject));
        }
        manager.flush();
    }

    @Override
    public void withdrawAllForKeyPair(KeyPairEntity keyPair) {
        createQuery("UPDATE PublishedObject po" +
            "   SET po.version = po.version + 1," +
            "       po.updatedAt = :now," +
            "       po.status = CASE po.status" +
            "                   WHEN :toBePublished THEN :withdrawn" +
            "                   WHEN :published     THEN :toBeWithdrawn" +
            "                   END" +
            " WHERE po.issuingKeyPair.id = :issuingKeyPair" +
            "   AND po.status IN (:toBePublished, :published)")
            .setParameter("now", Instant.now())
            .setParameter("issuingKeyPair", keyPair.getId())
            .setParameter("toBePublished", PublicationStatus.TO_BE_PUBLISHED)
            .setParameter("toBeWithdrawn", PublicationStatus.TO_BE_WITHDRAWN)
            .setParameter("published", PublicationStatus.PUBLISHED)
            .setParameter("withdrawn", PublicationStatus.WITHDRAWN)
            .executeUpdate();
    }

    @Override
    public void withdrawAllForDeletedKeyPair(KeyPairEntity keyPair) {
        withdrawAllForKeyPair(keyPair);
        createQuery("UPDATE PublishedObject po " +
            "    SET po.version = po.version + 1," +
            "        po.updatedAt = :now," +
            "        po.issuingKeyPair = null," +
            "        po.containingManifest = null" +
            "  WHERE po.issuingKeyPair.id = :issuingKeyPair")
            .setParameter("now", Instant.now())
            .setParameter("issuingKeyPair", keyPair.getId())
            .executeUpdate();
    }

    @Override
    public int updatePublicationStatus() {
        // First update `TO_BE_WITHDRAWN` before updating `TO_BE_PUBLISHED` to ensure
        // unique constraint is not violated.
        Instant now = Instant.now();
        int count = createQuery("UPDATE PublishedObject po" +
            "   SET po.version = po.version + 1," +
            "       po.updatedAt = :now," +
            "       po.status = :withdrawn" +
            " WHERE po.status = :toBeWithdrawn")
            .setParameter("now", now)
            .setParameter("toBeWithdrawn", PublicationStatus.TO_BE_WITHDRAWN)
            .setParameter("withdrawn", PublicationStatus.WITHDRAWN)
            .executeUpdate();
        count += createQuery("UPDATE PublishedObject po" +
            "   SET po.version = po.version + 1," +
            "       po.updatedAt = :now," +
            "       po.status = :published" +
            " WHERE po.status = :toBePublished")
            .setParameter("now", now)
            .setParameter("toBePublished", PublicationStatus.TO_BE_PUBLISHED)
            .setParameter("published", PublicationStatus.PUBLISHED)
            .executeUpdate();
        return count;
    }

    @Override
    public int deleteExpiredObjects(DateTime expirationTime) {
        Validate.isTrue(expirationTime.isBeforeNow(), "expiration time must be in the past");
        return createQuery(
            "DELETE FROM PublishedObject po " +
                "WHERE po.status = :withdrawn " +
                "  AND po.validityPeriod.notValidAfter < :expirationTime" +
                "  AND NOT EXISTS (FROM OutgoingResourceCertificate rc WHERE rc.publishedObject = po)" +
                "  AND NOT EXISTS (FROM CrlEntity crl WHERE crl.publishedObject = po) " +
                "  AND NOT EXISTS (FROM ManifestEntity mft WHERE mft.publishedObject = po) " +
                "  AND NOT EXISTS (FROM RoaEntity roa WHERE roa.publishedObject = po)" +
                "  AND NOT EXISTS (FROM AspaEntity aspa WHERE aspa.publishedObject = po)")
            .setParameter("withdrawn", PublicationStatus.WITHDRAWN)
            .setParameter("expirationTime", expirationTime)
            .executeUpdate();
    }

    @Override
    protected Class<PublishedObject> getEntityClass() {
        return PublishedObject.class;
    }
}
