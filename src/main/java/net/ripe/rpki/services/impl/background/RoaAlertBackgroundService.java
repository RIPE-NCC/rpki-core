package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.core.services.background.ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.services.impl.RoaAlertChecker;

import java.util.Map;

@Slf4j
abstract class RoaAlertBackgroundService extends ConcurrentBackgroundServiceWithAdminPrivilegesOnActiveNode {

    private final RoaAlertConfigurationViewService roaAlertConfigurationViewService;
    private final RoaAlertChecker roaAlertChecker;
    private final RoaAlertFrequency frequency;

    public RoaAlertBackgroundService(BackgroundTaskRunner backgroundTaskRunner,
                                     RoaAlertConfigurationViewService roaAlertConfigurationViewService,
                                     RoaAlertChecker roaAlertChecker,
                                     RoaAlertFrequency frequency) {
        super(backgroundTaskRunner);
        this.roaAlertConfigurationViewService = roaAlertConfigurationViewService;
        this.roaAlertChecker = roaAlertChecker;
        this.frequency = frequency;
    }

    @Override
    protected void runService(Map<String, String> parameters) {
        roaAlertConfigurationViewService.findByFrequency(frequency).forEach(alertSubscription -> {
            try {
                roaAlertChecker.checkAndSendRoaAlertEmailToSubscription(alertSubscription);
            } catch (RuntimeException e) {
                log.error(String.format("Checking %s alert subscription %s failed: %s",
                    frequency.name().toLowerCase(), alertSubscription, e.getMessage()), e);
            }
        });
    }
}
