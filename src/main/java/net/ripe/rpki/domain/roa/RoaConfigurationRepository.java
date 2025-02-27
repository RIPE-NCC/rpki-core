package net.ripe.rpki.domain.roa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public interface RoaConfigurationRepository {
    Optional<RoaConfiguration> findByCertificateAuthority(ManagedCertificateAuthority certificateAuthority);

    /**
     * Gets the ROA configuration associated with <code>certificateAuthority</code>.
     *
     * @return the ROA configuration (never null).
     */
    RoaConfiguration getOrCreateByCertificateAuthority(ManagedCertificateAuthority certificateAuthority);

    Collection<RoaConfiguration> findAll();

    Collection<RoaConfigurationPrefixData> findAllPrefixes();

    int countRoaPrefixes();

    Optional<Instant> lastModified();

    void remove(RoaConfiguration roaConfiguration);

    default void addPrefixes(RoaConfiguration roaConfiguration, Collection<RoaConfigurationPrefix> prefixes) {
        mergePrefixes(roaConfiguration, prefixes, Collections.emptyList());
    }

    default void removePrefixes(RoaConfiguration roaConfiguration, Collection<RoaConfigurationPrefix> prefixes) {
        mergePrefixes(roaConfiguration, Collections.emptyList(), prefixes);
    }

    RoaConfiguration.PrefixDiff mergePrefixes(RoaConfiguration configuration,
                                              Collection<RoaConfigurationPrefix> prefixesToAdd,
                                              Collection<RoaConfigurationPrefix> prefixesToRemove);
}
