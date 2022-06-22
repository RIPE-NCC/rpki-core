package net.ripe.rpki.domain.aspa;

import net.ripe.rpki.domain.HostedCertificateAuthority;

import java.util.Collection;
import java.util.Optional;

public interface AspaConfigurationRepository {
    Optional<AspaConfiguration> findByCertificateAuthority(HostedCertificateAuthority certificateAuthority);

    Collection<AspaConfiguration> findAll();
}
