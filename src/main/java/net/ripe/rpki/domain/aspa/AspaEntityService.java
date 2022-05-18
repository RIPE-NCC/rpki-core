package net.ripe.rpki.domain.aspa;

import net.ripe.rpki.domain.HostedCertificateAuthority;

public interface AspaEntityService {

    void aspaConfigurationUpdated(HostedCertificateAuthority ca);

}
