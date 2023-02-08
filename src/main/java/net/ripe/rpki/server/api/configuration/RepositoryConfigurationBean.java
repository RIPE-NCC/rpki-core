package net.ripe.rpki.server.api.configuration;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.ConfigurationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

@Component
@Primary
@Slf4j
public class RepositoryConfigurationBean implements RepositoryConfiguration {
    private final URI notificationUri;
    private final URI publicRepositoryUri;
    private final URI taRepositoryUri;
    private final String productionCaName;
    private final String allResourcesCaName;
    private final File localRepositoryDirectory;
    private final File localTrustAnchorRepositoryDirectory;

    @Autowired
    public RepositoryConfigurationBean(
            @Value("${" + RepositoryConfiguration.ONLINE_REPOSITORY_NOTIFICATION_URI + "}") String notificationUriString,
            @Value("${" + RepositoryConfiguration.ONLINE_REPOSITORY_BASE_URI + "}") String publicRepositoryUriString,
            @Value("${" + RepositoryConfiguration.ONLINE_REPOSITORY_BASE_DIRECTORY + "}") String localRepositoryDirectoryString,
            @Value("${" + RepositoryConfiguration.TA_REPOSITORY_BASE_URI + "}") String taRepositoryUriString,
            @Value("${" + RepositoryConfiguration.TA_REPOSITORY_BASE_DIRECTORY + "}") String localTrustAnchorRepositoryDirectoryString,
            @Value("${" + RepositoryConfiguration.PRODUCTION_CA_NAME + "}") String productionCaName,
            @Value("${" + RepositoryConfiguration.ALL_RESOURCES_CA_NAME + "}") String allResourcesCaName) {
        this.productionCaName = productionCaName;
        this.allResourcesCaName = allResourcesCaName;
        this.localRepositoryDirectory = validateLocalRepositoryDirectory(ConfigurationUtil.interpolate(localRepositoryDirectoryString));
        this.notificationUri = makeUri(notificationUriString);
        this.taRepositoryUri = makeUri(slashIt(taRepositoryUriString));
        this.localTrustAnchorRepositoryDirectory = validateLocalRepositoryDirectory(ConfigurationUtil.interpolate(localTrustAnchorRepositoryDirectoryString));
        this.publicRepositoryUri = makeUri(slashIt(publicRepositoryUriString));
    }

    private File validateLocalRepositoryDirectory(String localRepositoryDirectoryString) {
        File repository = new File(localRepositoryDirectoryString);

        if (!repository.isDirectory()) {
            try {
                log.info("Creating local repository '{}' as it does not yet exist.", repository.toPath());
                Files.createDirectories(repository.toPath());
            } catch (IOException e) {
                throw new CertificationConfigurationException("Local repository " +
                        " (" + repository.toPath() + " surely for testing!) does not exist and cannot be created", e);
            }
        }

        if (!repository.isDirectory() || !repository.canRead() || !repository.canWrite()) {
            throw new CertificationConfigurationException("Local repository directory '" +
                    repository.getAbsolutePath() + "' cannot be read, written, or is not a directory.");
        }

        return repository;
    }

    private static URI makeUri(String s) {
        return (s == null || s.trim().isEmpty()) ? null : URI.create(s);
    }

    private static String slashIt(String s) {
        return s.endsWith("/") ? s : s + "/";
    }


    @Override
    public File getLocalRepositoryDirectory() {
        return localRepositoryDirectory;
    }

    @Override
    public URI getPublicRepositoryUri() {
        return publicRepositoryUri;
    }

    @Override
    public URI getTrustAnchorRepositoryUri() {
        return taRepositoryUri;
    }

    @Override
    public File getLocalTrustAnchorRepositoryDirectory() {
        return localTrustAnchorRepositoryDirectory;
    }

    @Override
    public URI getNotificationUri() {
        return notificationUri;
    }

    @Override
    public X500Principal getProductionCaPrincipal() {
        return new X500Principal(productionCaName);
    }

    @Override
    public X500Principal getAllResourcesCaPrincipal() {
        return new X500Principal(allResourcesCaName);
    }

}
