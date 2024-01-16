package net.ripe.rpki.services.impl.jpa;

import net.ripe.ipresource.Asn;
import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.SortedMap;
import java.util.stream.Stream;

import static net.ripe.rpki.util.Streams.streamToSortedMap;

@Repository
@Transactional
public class JpaAspaConfigurationRepository extends JpaRepository<AspaConfiguration> implements AspaConfigurationRepository {

    @Override
    public SortedMap<Asn, AspaConfiguration> findByCertificateAuthority(ManagedCertificateAuthority ca) {
        Stream<AspaConfiguration> aspaConfigurationStream = manager
            .createQuery("from AspaConfiguration where certificateAuthority.id = :caId order by customerAsn", AspaConfiguration.class)
            .setParameter("caId", ca.getId())
            .getResultStream();
        return streamToSortedMap(
            aspaConfigurationStream,
            AspaConfiguration::getCustomerAsn,
            x -> x
        );
    }

    @Override
    public SortedMap<Asn, AspaConfiguration> findConfigurationsWithProvidersByCertificateAuthority(ManagedCertificateAuthority ca) {
        Stream<AspaConfiguration> aspaConfigurationStream = manager
                .createQuery("from AspaConfiguration ac where ac.certificateAuthority.id = :caId and ac.providers is not empty order by customerAsn", AspaConfiguration.class)
                .setParameter("caId", ca.getId())
                .getResultStream();
        return streamToSortedMap(
                aspaConfigurationStream,
                AspaConfiguration::getCustomerAsn,
                x -> x
        );
    }

    @Override
    protected Class<AspaConfiguration> getEntityClass() {
        return AspaConfiguration.class;
    }
}
