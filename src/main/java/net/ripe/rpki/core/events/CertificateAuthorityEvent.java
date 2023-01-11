package net.ripe.rpki.core.events;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CommandContext;

@EqualsAndHashCode
@ToString
public abstract class CertificateAuthorityEvent {

    @Getter
    @NonNull
    private final VersionedId certificateAuthorityVersionedId;

    protected CertificateAuthorityEvent(@NonNull VersionedId certificateAuthorityVersionedId) {
        this.certificateAuthorityVersionedId = certificateAuthorityVersionedId;
    }

    public long getCertificateAuthorityId() {
        return certificateAuthorityVersionedId.getId();
    }

    public abstract void accept(CertificateAuthorityEventVisitor visitor, CommandContext recording);
}
