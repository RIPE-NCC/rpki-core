package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.ManagedCertificateAuthority;
import net.ripe.rpki.domain.aspa.AspaConfiguration;
import net.ripe.rpki.domain.aspa.AspaConfigurationRepository;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.NoResultException;
import java.util.Optional;

@Repository
@Transactional
public class JpaAspaConfigurationRepository extends JpaRepository<AspaConfiguration> implements AspaConfigurationRepository {

    @Override
    public Optional<AspaConfiguration> findByCertificateAuthority(ManagedCertificateAuthority ca) {
        try {
            return Optional.of(
                (AspaConfiguration) createQuery("from AspaConfiguration where certificateAuthority.id = :caId")
                    .setParameter("caId", ca.getId())
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    protected Class<AspaConfiguration> getEntityClass() {
        return AspaConfiguration.class;
    }

}
