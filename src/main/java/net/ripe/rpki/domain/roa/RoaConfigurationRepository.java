package net.ripe.rpki.domain.roa;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpResourceRange;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.server.api.support.objects.CaName;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    List<RoaConfigurationPerCa> findAllPerCa();

    void logRoaPrefixDeletion(RoaConfiguration configuration, Collection<? extends RoaConfigurationPrefix> deletedPrefixes);

    int countRoaPrefixes();

    Optional<Instant> lastModified();

    class RoaConfigurationPerCa {
        public final Long caId;
        public final CaName caName;
        public final Asn asn;
        public final IpResourceRange prefix;
        public final Integer maximumLength;
        public RoaConfigurationPerCa(Long caId, CaName caName, Asn asn, IpResourceRange prefix, Integer maximumLength) {
            this.caId = caId;
            this.caName = caName;
            this.asn = asn;
            this.prefix = prefix;
            this.maximumLength = maximumLength;
        }
    }
}
