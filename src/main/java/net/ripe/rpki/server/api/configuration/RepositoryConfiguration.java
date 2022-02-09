package net.ripe.rpki.server.api.configuration;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.net.URI;

public interface RepositoryConfiguration {

    String ONLINE_REPOSITORY_BASE_URI = "online.repository.uri";
    String ONLINE_REPOSITORY_BASE_DIRECTORY = "online.repository.directory";

    String ONLINE_REPOSITORY_NOTIFICATION_URI = "online.repository.notification.uri";

    String TA_REPOSITORY_BASE_URI = "ta.repository.uri";
    String TA_REPOSITORY_BASE_DIRECTORY = "ta.repository.directory";

    String PRODUCTION_CA_NAME = "production.ca.name";

    String ALL_RESOURCES_CA_NAME = "all.resources.ca.name";
    String RSYNC_TARGET_DIRECTORY_RETENTION_PERIOD_MINUTES = "rsync.target.directory.retention.period.minutes";
    String RSYNC_TARGET_DIRECTORY_RETENTION_COPIES_COUNT = "rsync.target.directory.retention.copies.count";

    URI getPublicRepositoryUri();

    URI getTrustAnchorRepositoryUri();

    URI getNotificationUri();

    File getLocalRepositoryDirectory();

    File getLocalTrustAnchorRepositoryDirectory();

    X500Principal getProductionCaPrincipal();

    X500Principal getAllResourcesCaPrincipal();
}
