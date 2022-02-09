package net.ripe.rpki.server.api.services.read;

import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;

import java.util.List;

public interface RoaAlertConfigurationViewService {

    List<RoaAlertConfigurationData> findAll();

    List<RoaAlertConfigurationData> findByFrequency(RoaAlertFrequency frequency);

    RoaAlertConfigurationData findRoaAlertSubscription(long caId);
}
