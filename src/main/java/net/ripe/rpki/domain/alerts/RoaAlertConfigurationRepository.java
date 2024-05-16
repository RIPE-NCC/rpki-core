package net.ripe.rpki.domain.alerts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface RoaAlertConfigurationRepository {
    void add(RoaAlertConfiguration entity);

    Collection<RoaAlertConfiguration> findAll();

    List<RoaAlertConfiguration> findByFrequency(RoaAlertFrequency frequency);

    RoaAlertConfiguration findByCertificateAuthorityIdOrNull(long caId);

    void remove(RoaAlertConfiguration entity);

    List<RoaAlertConfiguration> findByEmail(String email);

    Optional<RoaAlertConfiguration> findByUnsubscribeToken(UUID unsubscribeToken);
}
