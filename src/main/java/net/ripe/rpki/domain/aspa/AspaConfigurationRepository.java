package net.ripe.rpki.domain.aspa;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.ManagedCertificateAuthority;

import java.util.Collection;
import java.util.SortedMap;

public interface AspaConfigurationRepository {
    SortedMap<Asn, AspaConfiguration> findByCertificateAuthority(ManagedCertificateAuthority certificateAuthority);

    Collection<AspaConfiguration> findAll();

    void add(AspaConfiguration aspaConfiguration);
    void remove(AspaConfiguration aspaConfiguration);
}
