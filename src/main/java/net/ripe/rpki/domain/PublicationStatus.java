package net.ripe.rpki.domain;

import java.util.EnumSet;

/**
 * Objects that need to be published in the public repository are tracked by {@link PublishedObject}s.
 *
 * Every {@link PublishedObject} has a publication status. New objects have the {@code TO_BE_PUBLISHED} status
 * and are not yet ready to be published (since they are not yet in the manifest).
 *
 * Once the object is included in the manifest the status changes to {@code PUBLISHED}, indicating that the
 * object can now be put in the public repository (together with the manifest to ensure consistency!).
 *
 * Once an object is no longer valid it will be withdrawn. In case the object was not yet included in a manifest
 * the status changes to {@code WITHDRAWN} and the object will never be put in the public repository.
 *
 * If the object was already in the public repository the status change to {@code TO_BE_WITHDRAWN}. Once the new
 * manifest is generated that no longer includes this object the status switched to {@code WITHDRAWN}, indicating
 * it can be removed from the public repository when the new manifest is published there.
 *
 * The {@link net.ripe.rpki.services.impl.background.PublicRepositoryPublicationServiceBean} ensures that all
 * published objects from all CAs are updated atomically. So at any point in time all published object with
 * status {@code PUBLISHED} and {@code TO_BE_WITHDRAWN} is the set of objects that should be available in
 * the public repository. This ensures we can publish a consistent set of objects.
 */
public enum PublicationStatus {
    /**
     * An object that needs to included in the manifest before it can be published to the publication service.
     */
    TO_BE_PUBLISHED(false),

    /**
     * An object that is longer valid but is still part of the manifest and therefore still needs to be published
     * to the publication service.
     */
    TO_BE_WITHDRAWN(true),

    /**
     * An object that is part of a valid manifest and needs to be published to the publication service.
     */
    PUBLISHED(true),

    /**
     * An object that is no longer valid and not part of a manifest. Therefore it needs to be absent from the
     * public repository.
     */
    WITHDRAWN(false);

    private final boolean published;

    PublicationStatus(boolean published) {
        this.published = published;
    }

    public boolean isPublished() {
        return published;
    }

    /**
     * The statuses that indicate objects that are valid and should be part of the manifest when it is updated.
     */
    public static EnumSet<PublicationStatus> ACTIVE_STATUSES = EnumSet.of(TO_BE_PUBLISHED, PUBLISHED);

    /**
     * The statuses that indicate objects that are part of the manifest and available for publication in the
     * public repository.
     */
    public static EnumSet<PublicationStatus> PUBLISHED_STATUSES = EnumSet.of(PUBLISHED, TO_BE_WITHDRAWN);

    /**
     * The statuses that indicate objects that need processing to be added or removed from the manifest
     * and public repository.
     */
    public static EnumSet<PublicationStatus> PENDING_STATUSES = EnumSet.of(TO_BE_PUBLISHED, TO_BE_WITHDRAWN);
}
