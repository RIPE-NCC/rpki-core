package net.ripe.rpki.domain;

import net.ripe.rpki.ncc.core.domain.support.EntitySupport;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.SequenceGenerator;
import java.net.URI;
import java.util.Arrays;

import static net.ripe.rpki.domain.PublicationStatus.PUBLISHED;
import static net.ripe.rpki.domain.PublicationStatus.TO_BE_PUBLISHED;
import static net.ripe.rpki.domain.PublicationStatus.TO_BE_WITHDRAWN;
import static net.ripe.rpki.domain.PublicationStatus.WITHDRAWN;

@MappedSuperclass
abstract public class GenericPublishedObject extends EntitySupport {

    @Id
    @SequenceGenerator(name = "seq_published_object", sequenceName = "seq_all", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_published_object")
    protected Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    protected PublicationStatus status;

    @Column(nullable = false)
    protected byte[] content;


    public Long getId() {
        return id;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public abstract URI getUri();

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    public boolean isPending() {
        return (status == PublicationStatus.TO_BE_PUBLISHED) || (status == PublicationStatus.TO_BE_WITHDRAWN);
    }

    /**
     * Record result of successful publication in the public repository.
     */
    public void updateStatus() {
        switch (status) {
            case TO_BE_WITHDRAWN:
                status = PublicationStatus.WITHDRAWN;
                break;
            case TO_BE_PUBLISHED:
                status = PublicationStatus.PUBLISHED;
                break;
            case PUBLISHED:
            case WITHDRAWN:
                throw new IllegalStateException("Published object with incorrect status for update: " + status + " at URI " + getUri().toString());
        }
    }

    /**
     * Mark object to be published in the public repository.
     */
    public void publish() {
        switch (status) {
            case TO_BE_PUBLISHED:
            case PUBLISHED:
                status = TO_BE_PUBLISHED;
                break;
            case TO_BE_WITHDRAWN:
            case WITHDRAWN:
                throw new IllegalStateException("Cannot publish a (to be) withdrawn object: " + toString());
        }
    }

    /**
     * Mark object as published in the public repository.
     */
    public void published() {
        switch (status) {
            case TO_BE_PUBLISHED:
            case PUBLISHED:
                status = PUBLISHED;
                break;
            case TO_BE_WITHDRAWN:
            case WITHDRAWN:
                throw new IllegalStateException("Cannot publish a (to be) withdrawn object: " + toString());
        }
    }

    /**
     * Mark object to be withdrawn from the public repository.
     */
    public void withdraw() {
        switch (status) {
            case TO_BE_PUBLISHED:
                status = WITHDRAWN;
                break;
            case PUBLISHED:
                status = TO_BE_WITHDRAWN;
                break;
            case TO_BE_WITHDRAWN:
            case WITHDRAWN:
                break;
        }
    }

    /**
     * Mark object as withdrawn in the public repository.
     */
    public void withdrawn() {
        switch (status) {
            case TO_BE_PUBLISHED:
            case PUBLISHED:
                throw new IllegalStateException("cannot mark object withdrawn in status " + status);
            case TO_BE_WITHDRAWN:
            case WITHDRAWN:
                status = WITHDRAWN;
                break;
        }
    }
}
