package net.ripe.rpki.domain.audit;

import net.ripe.rpki.core.events.CertificateAuthorityEvent;

public interface EventSerializerService {

    String eventType(CertificateAuthorityEvent event);

    String serialize(CertificateAuthorityEvent event);

    CertificateAuthorityEvent deserialize(String event);
}
