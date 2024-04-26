package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.TrustAnchorPublishedObject;
import net.ripe.rpki.domain.TrustAnchorPublishedObjectRepository;
import org.joda.time.Instant;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@Component
public class JpaTrustAnchorPublishedObjectRepository implements TrustAnchorPublishedObjectRepository {

    @PersistenceContext
    protected EntityManager manager;

    @Override
    public List<TrustAnchorPublishedObject> findActiveObjects() {
        return manager.createQuery("select po from TrustAnchorPublishedObject po where po.status IN :active", TrustAnchorPublishedObject.class)
                .setParameter("active", PublicationStatus.ACTIVE_STATUSES)
                .getResultList();
    }

    @Override
    public TrustAnchorPublishedObject add(TrustAnchorPublishedObject publishedObject) {
        manager.persist(publishedObject);
        return publishedObject;
    }

    @Override
    public void persist(Iterable<TrustAnchorPublishedObject> publishedObjects) {
        for (TrustAnchorPublishedObject publishedObject : publishedObjects) {
            manager.merge(publishedObject);
        }
        manager.flush();
    }

    @Override
    public int updatePublicationStatus() {
        // First update `TO_BE_WITHDRAWN` before updating `TO_BE_PUBLISHED` to ensure
        // unique constraint is not violated.
        Instant now = Instant.now();
        int count = manager.createQuery("UPDATE TrustAnchorPublishedObject po" +
            "   SET po.version = po.version + 1," +
            "       po.updatedAt = :now," +
            "       po.status = :withdrawn" +
            " WHERE po.status = :toBeWithdrawn")
            .setParameter("now", now)
            .setParameter("toBeWithdrawn", PublicationStatus.TO_BE_WITHDRAWN)
            .setParameter("withdrawn", PublicationStatus.WITHDRAWN)
            .executeUpdate();
        count += manager.createQuery("UPDATE TrustAnchorPublishedObject po" +
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
}
