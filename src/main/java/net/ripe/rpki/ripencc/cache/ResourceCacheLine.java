package net.ripe.rpki.ripencc.cache;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.server.api.support.objects.CaName;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "resource_cache")
class ResourceCacheLine {

    @Id
    private String name;
    private String resources;

    protected ResourceCacheLine() {
        //for hibernate
    }

    public ResourceCacheLine(final CaName user, final IpResourceSet resources) {
        this.name = user.toString();
        this.resources = resources.toString();
    }

    public CaName getName() {
        return CaName.parse(name);
    }

    public IpResourceSet getResources() {
        return IpResourceSet.parse(resources);
    }

}
