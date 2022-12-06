package net.ripe.rpki.services.impl.background;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.core.services.background.BackgroundServiceExecutionResult;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BackgroundJob implements Job {

    static final String BACKGROUND_SERVICE_KEY = "background-service-bean-name";

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    BackgroundServiceMetrics backgroundServiceMetrics;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void execute(JobExecutionContext context) {
        final JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        final String name = jobDataMap.getString(BACKGROUND_SERVICE_KEY);

        try {
            backgroundServiceMetrics.trackStartTime(name);

            Map<String, String> parameters = new HashMap<>();
            for (Map.Entry<String, Object> entry : context.getTrigger().getJobDataMap().entrySet()) {
                if (entry.getValue() instanceof String) {
                    parameters.put(entry.getKey(), (String) entry.getValue());
                }
            }

            BackgroundServiceExecutionResult result = applicationContext.getBean(name, BackgroundService.class).execute(parameters);

            backgroundServiceMetrics.trackResult(name, result);
        } catch (BeansException e) {
            log.error("Background service {} is not set or cannot be found in the application context", name, e);
            backgroundServiceMetrics.trackFailure(name);
        } catch (Exception e) {
            backgroundServiceMetrics.trackFailure(name);
            throw e;
        }
    }
}
