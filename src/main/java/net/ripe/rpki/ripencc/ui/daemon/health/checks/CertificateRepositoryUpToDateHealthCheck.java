package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class CertificateRepositoryUpToDateHealthCheck extends Health.Check {
    // if there is no user activity, repository only updates every 8 hours
    public static final Duration MAXIMUM_TIME_FOR_REPO_UPDATES =
            Duration.standardHours(8).plus(Duration.standardMinutes(5));

    private final RepositoryConfiguration configuration;

    @Autowired
    public CertificateRepositoryUpToDateHealthCheck(RepositoryConfiguration configuration) {
        super("certificate-repository-uptodate");
        this.configuration = configuration;
    }

    @Override
    public Health.Status check() {
        final File localRepositoryDirectory = configuration.getLocalRepositoryDirectory();
        final File publishedDir = new File(localRepositoryDirectory.getAbsolutePath() + "/published");
        final DateTime lastModified = new DateTime(publishedDir.lastModified());
        if (lastModified.plus(MAXIMUM_TIME_FOR_REPO_UPDATES).isAfterNow()) {
            return Health.ok(humanFriendly(lastModified));
        }
        return Health.error(humanFriendly(lastModified));
    }

    private String humanFriendly(DateTime lastUpdateTime) {
        return String.format("last updated %s ago (at %s)",
                new Period(lastUpdateTime, new DateTime()).withMillis(0).toString(PeriodFormat.getDefault()),
                lastUpdateTime);
    }
}
