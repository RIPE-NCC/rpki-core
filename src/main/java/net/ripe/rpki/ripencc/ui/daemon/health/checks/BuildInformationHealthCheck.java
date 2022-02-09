package net.ripe.rpki.ripencc.ui.daemon.health.checks;

import net.ripe.rpki.ripencc.ui.daemon.health.Health;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Named;
import java.util.Properties;

//@Component
public class BuildInformationHealthCheck extends Health.Check {

    private final Properties buildInfo;

    @Autowired
    public BuildInformationHealthCheck(@Named("buildInfo") Properties buildInfo) {
        super("build-information");
        this.buildInfo = buildInfo;
    }

    @Override
    public Health.Status check() {
        String buildNumber = buildInfo.getProperty("build.number", "<unknown>");
        String buildTimestamp = buildInfo.getProperty("build.timestamp", "<unknown>");
        String revisionNumber = buildInfo.getProperty("revision.number", "<unknown>");
        String buildInformation = "Build number: " + buildNumber + "\tBuild timestamp: " + buildTimestamp + "\tRevision number: " + revisionNumber;

        return Health.ok(buildInformation);
    }
}