package net.ripe.rpki.domain;

import java.util.List;

public interface TrustAnchorPublishedObjectRepository {
    List<TrustAnchorPublishedObject> findActiveObjects();

    TrustAnchorPublishedObject add(TrustAnchorPublishedObject publishedObject);

    void persist(Iterable<TrustAnchorPublishedObject> publishedObjects);

    int updatePublicationStatus();
}
