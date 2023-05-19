package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.services.impl.RoaAlertChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("!pilot")
@Service("roaAlertBackgroundServiceDaily")
public class RoaAlertBackgroundServiceDailyBean extends RoaAlertBackgroundService {

    @Autowired
    public RoaAlertBackgroundServiceDailyBean(BackgroundTaskRunner backgroundTaskRunner,
                                              RoaAlertConfigurationViewService roaAlertConfigurationViewService,
                                              RoaAlertChecker roaAlertChecker) {
        super(backgroundTaskRunner, roaAlertConfigurationViewService, roaAlertChecker, RoaAlertFrequency.DAILY);
    }

    @Override
    public String getName() {
        return "ROA Alert Background Service Daily";
    }
}
