package net.ripe.rpki.publication.persistence.disk;

import net.ripe.rpki.commons.util.ConfigurationUtil;
import net.ripe.rpki.domain.PublishedObjectData;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Component
public class FileSystemPublicationObjectPersistence {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemPublicationObjectPersistence.class);

    // This pattern needs to match both published directory names (`published-2021-04-26T09:57:59.034Z`) and temporary
    // directory names (`tmp-2021-04-26T10:09:06.023Z-4352054854289820810`).
    public static final Pattern PUBLICATION_DIRECTORY_PATTERN = Pattern.compile("^(tmp|published)-\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z(-\\d+)?$");

    // Internal directories (used to store all the RPKI objects per CA, etc) are set to this modification time so
    // that rsync does not see the directories as modified every time we fully write the repository. The RPKI objects
    // have their creation time as last modified time, so rsync will copy these as needed.
    public static final FileTime INTERNAL_DIRECTORY_LAST_MODIFIED_TIME = FileTime.fromMillis(0);

    private final Map<URI, Path> baseUriToBaseDir;

    private final ForkJoinPool fileWriterPool = new ForkJoinPool(2 * Runtime.getRuntime().availableProcessors());
    private final long targetDirectoryRetentionPeriodMs;
    private final long targetDirectoryRetentionCopiesCount;

    @Inject
    public FileSystemPublicationObjectPersistence(
        @Value("${" + RepositoryConfiguration.ONLINE_REPOSITORY_BASE_URI + "}") URI onlineRepositoryBaseUri,
        @Value("${" + RepositoryConfiguration.ONLINE_REPOSITORY_BASE_DIRECTORY + "}") String onlineRepositoryBaseDirectory,
        @Value("${" + RepositoryConfiguration.TA_REPOSITORY_BASE_URI + "}") URI taRepositoryBaseUri,
        @Value("${" + RepositoryConfiguration.TA_REPOSITORY_BASE_DIRECTORY + "}") String taRepositoryBaseDirectory,
        @Value("${" + RepositoryConfiguration.RSYNC_TARGET_DIRECTORY_RETENTION_PERIOD_MINUTES + ":120}") long targetDirectoryRetentionPeriodMinutes,
        @Value("${" + RepositoryConfiguration.RSYNC_TARGET_DIRECTORY_RETENTION_COPIES_COUNT + ":8}") long targetDirectoryRetentionCopiesCount
    ) throws IOException {

        baseUriToBaseDir = new LinkedHashMap<>();
        baseUriToBaseDir.put(enforceTrailingSlash(onlineRepositoryBaseUri), new File(ConfigurationUtil.interpolate(onlineRepositoryBaseDirectory)).toPath());
        baseUriToBaseDir.put(enforceTrailingSlash(taRepositoryBaseUri), new File(ConfigurationUtil.interpolate(taRepositoryBaseDirectory)).toPath());

        targetDirectoryRetentionPeriodMs = TimeUnit.MINUTES.toMillis(targetDirectoryRetentionPeriodMinutes);
        this.targetDirectoryRetentionCopiesCount = Math.max(1, targetDirectoryRetentionCopiesCount);

        initialize();
    }

    private URI enforceTrailingSlash(URI uri) {
        return (uri.toString().endsWith("/")) ? uri : URI.create(uri + "/");
    }

    private void initialize() throws IOException {
        for (Path repositoryBaseDirectory : baseUriToBaseDir.values()) {
            Files.createDirectories(repositoryBaseDirectory);
            Validate.isTrue(Files.isDirectory(repositoryBaseDirectory), "local repository directory " + repositoryBaseDirectory + " is not a directory");
            Validate.isTrue(Files.isWritable(repositoryBaseDirectory), "local repository directory " + repositoryBaseDirectory + " is not writeable");
            Validate.isTrue(Files.isExecutable(repositoryBaseDirectory), "local repository directory " + repositoryBaseDirectory + " is not executable");
        }
    }

    public void writeAll(List<PublishedObjectData> publishedObjects) {
        Map<Path, List<PublishedObjectData>> byBaseDirectory = publishedObjects.stream().collect(Collectors.groupingBy(po -> publicationBaseDirectory(po.getUri())));
        byBaseDirectory.entrySet().parallelStream().forEach(entry -> writeAllForBaseDirectory(entry.getKey(), entry.getValue()));
    }

    private void writeAllForBaseDirectory(Path baseDirectory, List<PublishedObjectData> publishedObjects) {
        LOG.info("publishing {} objects to publication base directory {}", publishedObjects.size(), baseDirectory);

        long now = DateTimeUtils.currentTimeMillis();
        try {
            Path targetDirectory = writeObjectsToNewTargetDirectory(now, baseDirectory, publishedObjects);

            atomicallyReplacePublishedSymlink(baseDirectory, targetDirectory);

            cleanupOldTargetDirectories(now, baseDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path writeObjectsToNewTargetDirectory(long now, Path baseDirectory, List<PublishedObjectData> publishedObjects) throws IOException {
        String formattedNow = new DateTime(now, DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime());

        Path targetDirectory = baseDirectory.resolve("published-" + formattedNow);
        if (Files.exists(targetDirectory)) {
            throw new IllegalStateException("target directory " + targetDirectory + " already exists");
        }

        Path temporaryDirectory = Files.createTempDirectory(baseDirectory, "tmp-" + formattedNow + "-");
        try {

            LOG.debug("Creating internal directories for {}", baseDirectory);
            Set<Path> directories = publishedObjects.stream().map(po -> temporaryLocation(temporaryDirectory, po.getUri()).getParent()).collect(Collectors.toSet());
            fileWriterPool.submit(() -> directories.parallelStream().forEach(directory -> {
                try {
                     Files.createDirectories(directory);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })).join();

            LOG.debug("writing {} published object files for {}", publishedObjects.size(), baseDirectory);
            fileWriterPool.submit(() -> publishedObjects.parallelStream().forEach((object) -> {
                try {
                    Path file = temporaryLocation(temporaryDirectory, object.getUri());
                    Files.write(file, object.getContent());
                    // rsync relies on the correct timestamp for fast synchronization
                    Files.setLastModifiedTime(file, FileTime.fromMillis(object.getCreatedAt().toEpochMilli()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })).join();

            // Set all internal directory last modified times to epoch so that rsync does not see the directories
            // as changed, only the objects contained in the directories.
            LOG.debug("setting last modification time of internal directories of {} to {}", baseDirectory, INTERNAL_DIRECTORY_LAST_MODIFIED_TIME);
            try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
                fileWriterPool.submit(() -> paths.parallel().filter(Files::isDirectory).forEach(directory -> {
                    try {
                        Files.setLastModifiedTime(directory, INTERNAL_DIRECTORY_LAST_MODIFIED_TIME);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })).join();
            }

            // Directory write is fully complete, rename temporary to target directory name
            Files.setLastModifiedTime(temporaryDirectory, FileTime.fromMillis(now));
            Files.setPosixFilePermissions(temporaryDirectory, PosixFilePermissions.fromString("rwxr-xr-x"));
            Files.move(temporaryDirectory, targetDirectory, ATOMIC_MOVE);

            LOG.info("published {} objects to {}", publishedObjects.size(), targetDirectory);

            return targetDirectory;
        } finally {
            try {
                FileUtils.deleteDirectory(temporaryDirectory.toFile());
            } catch (IOException ignored) {
            }
        }
    }

    private void atomicallyReplacePublishedSymlink(Path baseDirectory, Path targetDirectory) throws IOException {
        Path targetSymlink = baseDirectory.resolve("published");

        // Atomically replace the symlink to point to the new target directory. We cannot
        // atomically replace a symlink except by first creating a temporary one and then
        // renaming that to the final symlink, which will atomically replace it.
        // See https://unix.stackexchange.com/a/6786
        Path temporarySymlink = Files.createTempFile(baseDirectory, "published-", ".tmp");

        // Deleting the temporary file is needed here, but it may result in a race condition
        // again with another process. Hopefully this is fine, since only one process will
        // succeed in creating and renaming the symlink.
        Files.deleteIfExists(temporarySymlink);

        Path symlink = Files.createSymbolicLink(temporarySymlink, targetDirectory.getFileName());

        if (Files.exists(targetSymlink) && !Files.isSymbolicLink(targetSymlink)) {
            // Temporary feature to upgrade existing systems (which have a `published` directory) to symbolic link
            // based publication.
            Files.move(targetSymlink, baseDirectory.resolve("published.bak"));
        }

        Files.move(symlink, targetSymlink, ATOMIC_MOVE, REPLACE_EXISTING);
    }

    private void cleanupOldTargetDirectories(long now, Path baseDirectory) throws IOException {
        long cutoff = now - targetDirectoryRetentionPeriodMs;

        try (
            Stream<Path> oldDirectories = Files.list(baseDirectory)
                .filter(path -> PUBLICATION_DIRECTORY_PATTERN.matcher(path.getFileName().toString()).matches())
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(this::getLastModifiedTime).reversed())
                .skip(targetDirectoryRetentionCopiesCount)
                .filter((directory) -> getLastModifiedTime(directory).toMillis() < cutoff)
        ) {
            fileWriterPool.submit(() -> oldDirectories.parallel().forEach((directory) -> {
                LOG.info("removing old publication directory {}", directory);
                try {
                    FileUtils.deleteDirectory(directory.toFile());
                } catch (IOException e) {
                    LOG.warn("removing old publication directory {} failed", directory, e);
                }
            })).join();
        }
    }

    private FileTime getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path publicationBaseDirectory(URI uri) {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("URI must be relative: " + uri);
        }

        for (Map.Entry<URI, Path> entry : baseUriToBaseDir.entrySet()) {
            URI baseUri = entry.getKey();
            URI relative = baseUri.relativize(uri);
            if (!relative.isAbsolute()) {
                return entry.getValue();
            }
        }

        throw new IllegalArgumentException("URI does not match known base locations: " + uri);
    }

    private Path temporaryLocation(Path temporaryDirectory, URI uri) {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("URI must be relative: " + uri);
        }

        for (URI baseUri : baseUriToBaseDir.keySet()) {
            URI relative = baseUri.relativize(uri);
            if (!relative.isAbsolute()) {
                return temporaryDirectory.resolve(relative.toString());
            }
        }

        throw new IllegalArgumentException("URI does not match known base locations: " + uri);
    }
}
