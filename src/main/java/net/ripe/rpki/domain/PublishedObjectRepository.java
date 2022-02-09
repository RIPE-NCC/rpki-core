package net.ripe.rpki.domain;

import net.ripe.rpki.ripencc.support.persistence.Repository;
import org.joda.time.DateTime;

import java.util.EnumSet;
import java.util.List;

public interface PublishedObjectRepository extends Repository<PublishedObject> {

    /**
     * Finds all objects that should be on the manifest for the given key pair.
     * @param keyPair the key pair that will sign the manifest
     * @return the objects that should be on the manifest.
     */
    List<PublishedObject> findActiveManifestEntries(KeyPairEntity keyPair);

    /**
     * Finds all objects that should be published in the public repository. All these objects are
     * a manifest or are included in a valid manifest. The objects include both {@link PublishedObject}s
     * and {@link TrustAnchorPublishedObject}s.
     *
     * @return list of objects that should be part of the public repository
     */
    List<PublishedObjectData> findCurrentlyPublishedObjects();

    List<Long> findObjectIdsWithoutValidityPeriod();

    List<PublishedObjectEntry> findEntriesByPublicationStatus(EnumSet<PublicationStatus> statuses);

    void removeAll(List<PublishedObject> publishedObjects);

    void withdrawAllForKeyPair(KeyPairEntity keyPair);

    void withdrawAllForDeletedKeyPair(KeyPairEntity keyPair);

    int updatePublicationStatus();

    int deleteExpiredObjects(DateTime expirationTime);
}
