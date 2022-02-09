package net.ripe.rpki.core.events;

import net.ripe.rpki.commons.util.EqualsSupport;
import net.ripe.rpki.commons.util.VersionedId;
import org.apache.commons.lang.Validate;

import java.io.Serializable;

public abstract class CertificateAuthorityEvent extends EqualsSupport implements Serializable {

    private static final long serialVersionUID = 1L;

    private final VersionedId certificateAuthorityId; // Maybe change name to certificationAuthorityVersionedId? If so, need to migrate existing serialised history

    public CertificateAuthorityEvent(VersionedId certificateAuthorityId) {
        Validate.notNull(certificateAuthorityId, "certificateAuthorityId is required");
        this.certificateAuthorityId = certificateAuthorityId;
    }

    public VersionedId getCertificateAuthorityVersionedId() {
        return certificateAuthorityId;
    }

    public abstract void accept(CertificateAuthorityEventVisitor visitor);
}
