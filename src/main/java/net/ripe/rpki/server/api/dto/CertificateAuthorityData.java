package net.ripe.rpki.server.api.dto;

import lombok.Getter;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.support.objects.ValueObjectSupport;
import net.ripe.rpki.commons.ta.domain.request.TrustAnchorRequest;
import org.apache.commons.lang.Validate;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

/**
 * DTO for display purposes.
 */
@Getter
public abstract class CertificateAuthorityData extends ValueObjectSupport {

    private static final long serialVersionUID = 2L;

    private final VersionedId versionedId;
    private final UUID uuid;
    private final Long parentId;
    private final X500Principal name;
    private final CertificateAuthorityType type;
    private final IpResourceSet resources;
    private final TrustAnchorRequest trustAnchorRequest;

    public CertificateAuthorityData(VersionedId versionedId, X500Principal name,
                                    UUID uuid, Long parentId,
                                    CertificateAuthorityType type,
                                    IpResourceSet resources,
                                    TrustAnchorRequest trustAnchorRequest) {
        Validate.notNull(versionedId, "versionedId is required");
        Validate.notNull(name, "name is required");
        Validate.notNull(uuid, "uuid is required");
        Validate.notNull(type, "type is required");
        Validate.notNull(resources, "resources is required");
        this.versionedId = versionedId;
        this.name = name;
        this.uuid = uuid;
        this.parentId = parentId;
        this.type = type;
        this.resources = resources;
        this.trustAnchorRequest = trustAnchorRequest;
    }

    public long getId() {
        return versionedId.getId();
    }
}
