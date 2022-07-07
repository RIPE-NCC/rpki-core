package net.ripe.rpki.core.events;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CommandContext;
import org.apache.commons.lang.Validate;

@EqualsAndHashCode
@ToString
public abstract class CertificateAuthorityEvent {

    private final VersionedId certificateAuthorityId; // Maybe change name to certificationAuthorityVersionedId? If so, need to migrate existing serialised history

    public CertificateAuthorityEvent(VersionedId certificateAuthorityId) {
        Validate.notNull(certificateAuthorityId, "certificateAuthorityId is required");
        this.certificateAuthorityId = certificateAuthorityId;
    }

    public VersionedId getCertificateAuthorityVersionedId() {
        return certificateAuthorityId;
    }

    public abstract void accept(CertificateAuthorityEventVisitor visitor, CommandContext recording);
}
