package net.ripe.rpki.ncc.core.domain.support;

import net.ripe.rpki.commons.util.VersionedId;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AggregateRoot extends EntitySupport {

    @Id
    private long id;

    protected AggregateRoot() {
    }

    public AggregateRoot(long id) {
        this.id = id;
    }

    @Override
    public Long getId() {
        return id;
    }

    public VersionedId getVersionedId() {
        return new VersionedId(id, version == null ? VersionedId.INITIAL_VERSION + 1 : version.longValue());
    }

}
