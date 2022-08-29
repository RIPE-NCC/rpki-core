package net.ripe.rpki.domain.aspa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;

import java.util.Collection;
import java.util.Optional;

public interface AspaConfigurationRepository {
    Optional<AspaConfiguration> findByCertificateAuthority(ManagedCertificateAuthority certificateAuthority);

    Collection<AspaConfiguration> findAll();
}
