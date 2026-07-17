package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.bgpsec.BgpSecConfiguration;
import net.ripe.rpki.domain.bgpsec.BgpSecConfigurationRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaBgpSecConfigurationRepository extends JpaRepository<BgpSecConfiguration> implements BgpSecConfigurationRepository {

    @Override
    public List<BgpSecConfiguration> findByCertificateAuthority(ManagedCertificateAuthority ca) {
        return manager
                .createQuery("from BgpSecConfiguration where certificateAuthority.id = :caId order by asn", BgpSecConfiguration.class)
                .setParameter("caId", ca.getId())
                .getResultStream()
                .toList();
    }

    @Override
    public Optional<BgpSecConfiguration> findByCertificateAuthorityAndId(ManagedCertificateAuthority ca, Long id) {
        return manager
            .createQuery("FROM BgpSecConfiguration WHERE certificateAuthority = :ca AND id = :id", BgpSecConfiguration.class)
            .setParameter("ca", ca)
            .setParameter("id", id)
            .getResultStream()
            .findAny();
    }

    @Override
    public void removeById(ManagedCertificateAuthority ca, Long id) {
        manager.createQuery("""
                DELETE FROM BgpSecConfiguration
                WHERE certificateAuthority = :ca
                  AND id = :id
                """)
            .setParameter("ca", ca)
            .setParameter("id", id)
            .executeUpdate();
    }

    @Override
    protected Class<BgpSecConfiguration> getEntityClass() {
        return BgpSecConfiguration.class;
    }
}
