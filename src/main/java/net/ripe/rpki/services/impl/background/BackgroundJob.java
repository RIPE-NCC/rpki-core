package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.core.services.background.BackgroundServiceTimings;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class BackgroundJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJob.class);

    static final String BACKGROUND_SERVICE_KEY = "background-service-bean-name";

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    BackgroundServiceMetrics backgroundServiceMetrics;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void execute(JobExecutionContext context) {
        final JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        final String name = (String) jobDataMap.get(BACKGROUND_SERVICE_KEY);
        try {
            backgroundServiceMetrics.started(name);
            final BackgroundServiceTimings result = applicationContext.getBean(name, BackgroundService.class).execute();
            backgroundServiceMetrics.finished(name, result);
        } catch (BeansException e) {
            log.error("Background service {} is not set or cannot be found in the application context", name, e);
        } catch (RuntimeException e) {
            backgroundServiceMetrics.failed(name);
            throw e;
        }
    }
}
