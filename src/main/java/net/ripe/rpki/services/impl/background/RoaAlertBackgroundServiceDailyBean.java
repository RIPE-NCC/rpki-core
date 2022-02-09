package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import net.ripe.rpki.services.impl.RoaAlertChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("roaAlertBackgroundServiceDaily")
public class RoaAlertBackgroundServiceDailyBean extends RoaAlertBackgroundService {

    @Autowired
    public RoaAlertBackgroundServiceDailyBean(ActiveNodeService propertyEntityService,
                                              RoaAlertConfigurationViewService roaAlertConfigurationViewService,
                                              RoaAlertChecker roaAlertChecker) {
        super(propertyEntityService, roaAlertConfigurationViewService, roaAlertChecker, RoaAlertFrequency.DAILY);
    }

    @Override
    public String getName() {
        return "ROA Alert Background Service Daily";
    }
}
