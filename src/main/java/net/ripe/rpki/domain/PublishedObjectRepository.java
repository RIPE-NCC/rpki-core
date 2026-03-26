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

    void withdrawAllForKeyPair(KeyPairEntity keyPair);

    void withdrawAllForDeletedKeyPair(KeyPairEntity keyPair);

    /**
     * Mark objects as published/withdrawn so that they will be sent to the RRDP/rsync repositories on the next
     * run of these background services.
     *
     * <p>IMPORTANT: a parent CA must always be published before a child CA to avoid invalidating a child CA's
     * certificates due to over-claiming resources!</p>
     *
     * @param issuingKeyPair the issuing key pair to publish objects for.
     * @return the number of objects published or withdrawn.
     */
    int publishObjects(KeyPairEntity issuingKeyPair);

    int deleteExpiredObjects(DateTime expirationTime);

    /**
     * Withdraw all <code>TO_BE_WITHDRAWN</code> objects issued by deleted key pairs (changing the status to <code>WITHDRAWN</code>>).
     */
    int withdrawObjectsForDeletedKeys();
}
