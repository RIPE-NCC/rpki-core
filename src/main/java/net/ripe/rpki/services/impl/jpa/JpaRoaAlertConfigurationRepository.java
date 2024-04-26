package net.ripe.rpki.services.impl.jpa;

import net.ripe.rpki.domain.alerts.RoaAlertConfiguration;
import net.ripe.rpki.domain.alerts.RoaAlertConfigurationRepository;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.ripencc.support.persistence.JpaRepository;
import org.apache.commons.lang.Validate;
import org.springframework.stereotype.Component;

import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
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

    @SuppressWarnings("unchecked")
    @Override
    public List<RoaAlertConfiguration> findByEmail(String email) {
        Query query = createQuery("SELECT rac FROM RoaAlertConfiguration rac WHERE email LIKE :email");
        query.setParameter("email", "%" + email + "%");
        return query.getResultList();
    }
}
