package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.server.api.configuration.Environment;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import org.quartz.JobDataMap;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.quartz.CronScheduleBuilder.dailyAtHourAndMinute;
import static org.quartz.CronScheduleBuilder.weeklyOnDayAndHourAndMinute;
import static org.quartz.DateBuilder.IntervalUnit.*;
import static org.quartz.DateBuilder.futureDate;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

@Slf4j
@Component
public class BackgroundServices {

    public static final String RIS_WHOIS_UPDATE_SERVICE = "risWhoisUpdateService";
    public static final String KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE = "keyPairActivationManagementService";
    public static final String MEMBER_KEY_ROLLOVER_MANAGEMENT_SERVICE = "memberKeyRolloverManagementService";
    public static final String RESOURCE_CACHE_UPDATE_SERVICE = "resourceCacheUpdateService";
    public static final String MANIFEST_CRL_UPDATE_SERVICE = "manifestCrlUpdateService";
    public static final String PUBLIC_REPOSITORY_PUBLICATION_SERVICE = "publicRepositoryPublicationService";
    public static final String PUBLIC_REPOSITORY_RSYNC_SERVICE = "publicRepositoryRsyncService";
    public static final String PUBLIC_REPOSITORY_RRDP_SERVICE = "publicRepositoryRrdpService";
    public static final String CERTIFICATE_EXPIRATION_SERVICE = "certificateExpirationService";
    public static final String ALL_CA_CERTIFICATE_UPDATE_SERVICE = "allCertificateUpdateService";
    public static final String KEY_PAIR_REVOCATION_MANAGEMENT_SERVICE = "keyPairRevocationManagementService";
    public static final String PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE = "productionCaKeyRolloverManagementService";
    public static final String PUBLISHED_OBJECT_CLEAN_UP_SERVICE = "publishedObjectCleanUpService";
    public static final String CA_CLEAN_UP_SERVICE = "caCleanUpService";
    public static final String ROA_ALERT_BACKGROUND_SERVICE = "roaAlertBackgroundServiceDaily";
    public static final String ROA_ALERT_BACKGROUND_SERVICE_WEEKLY = "roaAlertBackgroundServiceWeekly";

    private final ApplicationContext applicationContext;

    @Value("${manifest.crl.update.interval.minutes}")
    private int manifestCrlUpdateIntervalMinutes;

    @Value("${public.repository.publication.interval.minutes}")
    private int publicRepositoryPublicationIntervalMinutes;

    @Value("${public.repository.rsync.interval.minutes}")
    private int publicRepositoryRsyncIntervalMinutes;

    @Value("${public.repository.rrdp.interval.minutes}")
    private int publicRepositoryRrdpIntervalMinutes;

    @Value("${resource.update.interval.hours}")
    private int resourceUpdateIntervalHours;

    @Value("${autokeyrollover.enable:false}")
    private boolean autoKeyRolloverEnable;
    @Value("${autokeyrollover.update.interval.days}")
    private int autoKeyRolloverUpdateIntervalDays;

    @Value("${keypair.activation.interval.hours}")
    private int keyPairActivationIntervalHours;

    @Value("${keypair.revocation.interval.hours}")
    private int keyPairRevocationIntervalHours;

    @Value("${certificate.expiration.service.interval.minutes:7}")
    private int certificateExpirationIntervalMinutes;

    @Value("${published.object.cleanup.service.interval.minutes:7}")
    private int publishedObjectCleanupServiceIntervalMinutes;

    @Value("${riswhoisdump.update.interval.hours}")
    private int riswhoisdumpUpdateIntervalHours;

    private final Scheduler scheduler;

    private final Map<String, BackgroundService> allServices;

    @Autowired
    public BackgroundServices(ApplicationContext applicationContext,
                              Scheduler scheduler,
                              Map<String, BackgroundService> allServices) {
        this.applicationContext = applicationContext;
        this.scheduler = scheduler;
        this.allServices = allServices;
    }

    @PostConstruct
    private void scheduleAll() throws SchedulerException {

        if (Environment.isLocal() || Environment.isTest()) {
            return;
        }

        schedule(MANIFEST_CRL_UPDATE_SERVICE,
                futureDate(3, MINUTE),
                repeat().withIntervalInMinutes(manifestCrlUpdateIntervalMinutes));

        schedule(PUBLIC_REPOSITORY_PUBLICATION_SERVICE,
                futureDate(6, MINUTE),
                repeat().withIntervalInMinutes(publicRepositoryPublicationIntervalMinutes));

        schedule(PUBLIC_REPOSITORY_RSYNC_SERVICE,
                futureDate(7, MINUTE),
                repeat().withIntervalInMinutes(publicRepositoryRsyncIntervalMinutes));

        schedule(PUBLIC_REPOSITORY_RRDP_SERVICE,
                futureDate(7, MINUTE),
                repeat().withIntervalInMinutes(publicRepositoryRrdpIntervalMinutes));

        schedule(ALL_CA_CERTIFICATE_UPDATE_SERVICE,
                futureDate(resourceUpdateIntervalHours, HOUR),
                repeat().withIntervalInHours(resourceUpdateIntervalHours));

        if (autoKeyRolloverEnable) {
            schedule(PRODUCTION_CA_KEY_ROLLOVER_MANAGEMENT_SERVICE,
                     futureDate(autoKeyRolloverUpdateIntervalDays, DAY),
                     repeat().withIntervalInHours(autoKeyRolloverUpdateIntervalDays * 24));
            schedule(MEMBER_KEY_ROLLOVER_MANAGEMENT_SERVICE,
                     futureDate(autoKeyRolloverUpdateIntervalDays, DAY),
                     repeat().withIntervalInHours(autoKeyRolloverUpdateIntervalDays * 24));

            schedule(KEY_PAIR_ACTIVATION_MANAGEMENT_SERVICE,
                    futureDate(keyPairActivationIntervalHours, HOUR),
                    repeat().withIntervalInHours(keyPairActivationIntervalHours));
            schedule(KEY_PAIR_REVOCATION_MANAGEMENT_SERVICE,
                    futureDate(keyPairRevocationIntervalHours, HOUR),
                    repeat().withIntervalInHours(keyPairRevocationIntervalHours));
        }

        schedule(CERTIFICATE_EXPIRATION_SERVICE,
                futureDate(20, MINUTE),
                repeat().withIntervalInMinutes(certificateExpirationIntervalMinutes));

        schedule(PUBLISHED_OBJECT_CLEAN_UP_SERVICE,
                futureDate(22, MINUTE),
                repeat().withIntervalInMinutes(publishedObjectCleanupServiceIntervalMinutes));

        schedule(RIS_WHOIS_UPDATE_SERVICE,
                futureDate(10, SECOND),
                repeat().withIntervalInHours(riswhoisdumpUpdateIntervalHours));

        schedule(ROA_ALERT_BACKGROUND_SERVICE,
                futureDate(1, HOUR),
                dailyAtHourAndMinute(23, 23));

        schedule(ROA_ALERT_BACKGROUND_SERVICE_WEEKLY,
                futureDate(1, HOUR),
                weeklyOnDayAndHourAndMinute(2,23, 23));

        schedule(RESOURCE_CACHE_UPDATE_SERVICE,
                futureDate(3, MINUTE),
                repeat().withIntervalInMinutes(15));
    }

    private static SimpleScheduleBuilder repeat() {
        return simpleSchedule().repeatForever();
    }

    private <T extends Trigger> void schedule(String serviceName, Date startAt, ScheduleBuilder<T> schedule) throws SchedulerException {
        try {
            applicationContext.getBean(serviceName, BackgroundService.class);
        } catch (BeansException e) {
            throw new RuntimeException("Service '" + serviceName + "' can not be found in the application context", e);
        }
        final JobDataMap map = new JobDataMap();
        map.put(BackgroundJob.BACKGROUND_SERVICE_KEY, serviceName);
        scheduler.scheduleJob(
                newJob(BackgroundJob.class)
                        .withIdentity(serviceName)
                        .usingJobData(map)
                        .build(),
                newTrigger()
                        .withIdentity(serviceName + "Trigger")
                        .startAt(startAt)
                        .withSchedule(schedule)
                        .build()
        );
        log.info(String.format("Scheduled '%s', starting from '%s'", serviceName, startAt));
    }

    public void validate(String serviceName) {
        try {
            applicationContext.getBean(serviceName, BackgroundService.class);
        } catch (BeansException e) {
            throw new RuntimeException("Service '" + serviceName + "' can not be found in the application context", e);
        }
    }

    public BackgroundService getByName(String serviceName) {
        try {
            return applicationContext.getBean(serviceName, BackgroundService.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, BackgroundService> getAllServices() {
        return Collections.unmodifiableMap(allServices);
    }
}
