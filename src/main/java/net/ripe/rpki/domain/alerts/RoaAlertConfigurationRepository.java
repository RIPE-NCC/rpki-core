package net.ripe.rpki.domain.alerts;

import java.util.Collection;
import java.util.List;


public interface RoaAlertConfigurationRepository {
    void add(RoaAlertConfiguration entity);

    Collection<RoaAlertConfiguration> findAll();

    List<RoaAlertConfiguration> findByFrequency(RoaAlertFrequency frequency);

    RoaAlertConfiguration findByCertificateAuthorityIdOrNull(long caId);

    void remove(RoaAlertConfiguration entity);
}
