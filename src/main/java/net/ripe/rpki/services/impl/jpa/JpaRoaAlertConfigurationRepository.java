package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.apache.commons.lang.Validate;
import org.springframework.stereotype.Component;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.util.List;

@Component
public class JpaRoaAlertConfigurationRepository extends JpaRepository<RoaAlertConfiguration> implements RoaAlertConfigurationRepository {

    @Override
    protected Class<RoaAlertConfiguration> getEntityClass() {
        return RoaAlertConfiguration.class;
    }

    @Override
    public RoaAlertConfiguration findByCertificateAuthorityIdOrNull(long caId) {
        try {
            return (RoaAlertConfiguration) createQuery("from RoaAlertConfiguration where certificateAuthority.id = :caId").setParameter("caId", caId).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<RoaAlertConfiguration> findByFrequency(RoaAlertFrequency frequency) {
        Validate.notNull(frequency, "frequency is required");
        Query query = createQuery("from RoaAlertConfiguration where frequency = :frequency");
        query.setParameter("frequency", frequency);
        return query.getResultList();
    }
}
