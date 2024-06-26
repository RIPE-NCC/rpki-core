package net.ripe.rpki.ripencc.cache;

import lombok.Getter;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.server.api.support.objects.CaName;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "resource_cache")
class ResourceCacheLine {

    @Id
    private String name;
    @Getter
    private ImmutableResourceSet resources;

    protected ResourceCacheLine() {
        //for hibernate
    }

    public ResourceCacheLine(final CaName user, final ImmutableResourceSet resources) {
        this.name = user.toString();
        this.resources = resources;
    }

    public CaName getName() {
        return CaName.parse(name);
    }

}
