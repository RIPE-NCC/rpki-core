package net.ripe.rpki.publication.persistence.disk;

import com.google.common.collect.Sets;
import net.ripe.rpki.domain.PublishedObjectData;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static net.ripe.rpki.publication.persistence.disk.FileSystemPublicationObjectPersistence.INTERNAL_DIRECTORY_LAST_MODIFIED_TIME;
import static net.ripe.rpki.publication.persistence.disk.FileSystemPublicationObjectPersistence.PUBLICATION_DIRECTORY_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileSystemPublicationObjectPersistenceTest {

    private static final URI ONLINE_REPOSITORY_BASE_URI = URI.create("rsync://repository/repository/");
    private File onlineRepositoryBaseDirectory;

    private static final URI TA_REPOSITORY_BASE_URI = URI.create("rsync://repository/ta/");
    private File taRepositoryBaseDirectory;
    private static final Timestamp CREATED_AT = new Timestamp(System.currentTimeMillis());

    private FileSystemPublicationObjectPersistence subject;

    private static final byte[] CONTENTS;

    static {
        CONTENTS = new byte[100];
        new Random().nextBytes(CONTENTS);
    }

    @BeforeEach
    public void setUp(@TempDir File onlineRepositoryBaseDirectory, @TempDir File taRepositoryBaseDirectory) throws IOException {
        this.onlineRepositoryBaseDirectory = onlineRepositoryBaseDirectory;
        this.taRepositoryBaseDirectory = taRepositoryBaseDirectory;

        // fix the current time while a test is running
        DateTimeUtils.setCurrentMillisFixed(new DateTime().getMillis());

        subject = new FileSystemPublicationObjectPersistence(
            ONLINE_REPOSITORY_BASE_URI, onlineRepositoryBaseDirectory.toString(),
            TA_REPOSITORY_BASE_URI, taRepositoryBaseDirectory.toString(),
            120, 1);
    }

    @AfterEach
    public void tearDown() throws IOException {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void should_write_contents_of_publish_request_to_online_repository() throws IOException {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertThat(FileUtils.readFileToByteArray(new File(onlineRepositoryBaseDirectory, "published/foo/bar.cer"))).isEqualTo(CONTENTS);
    }

    @Test
    public void should_set_last_modification_time_of_published_object() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertThat(new File(onlineRepositoryBaseDirectory, "published/foo/bar.cer").lastModified() / 1000).isEqualTo(CREATED_AT.getTime() / 1000);
    }

    @Test
    public void should_set_last_modification_time_internal_directories() throws IOException {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/baz/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertThat(Files.getLastModifiedTime(new File(onlineRepositoryBaseDirectory, "published/foo").toPath())).isEqualTo(INTERNAL_DIRECTORY_LAST_MODIFIED_TIME);
        assertThat(Files.getLastModifiedTime(new File(onlineRepositoryBaseDirectory, "published/foo/baz").toPath())).isEqualTo(INTERNAL_DIRECTORY_LAST_MODIFIED_TIME);
    }

    @Test
    public void should_write_contents_of_publish_request_to_ta_repository() throws IOException {
        URI uri = TA_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertThat(FileUtils.readFileToByteArray(new File(taRepositoryBaseDirectory, "published/foo/bar.cer"))).isEqualTo(CONTENTS);
    }

    @Test
    public void should_set_symlink_to_latest_publication_directory() throws IOException {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        DateTimeUtils.setCurrentMillisFixed(1619000000000L);
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        Path targetDirectory = Files.readSymbolicLink(new File(onlineRepositoryBaseDirectory, "published").toPath());
        assertThat(targetDirectory.toString()).isEqualTo("published-2021-04-21T10:13:20.000Z");

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(60));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        targetDirectory = Files.readSymbolicLink(new File(onlineRepositoryBaseDirectory, "published").toPath());
        assertThat(targetDirectory.toString()).isEqualTo("published-2021-04-21T11:13:20.000Z");
    }

    @Test
    public void should_backup_old_published_directory_when_not_a_symbolic_link() throws IOException {
        // Temporary feature to upgrade existing systems (which have a `published` directory) to symbolic link
        // based publication.
        Path published = onlineRepositoryBaseDirectory.toPath().resolve("published");
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        Files.createDirectories(published);
        assertThat(published).isDirectory();

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertThat(published).isSymbolicLink();
        assertThat(published.resolveSibling("published.bak")).isDirectory();
    }

    @Test
    public void should_remove_publication_directories_older_than_retention_period() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        DateTimeUtils.setCurrentMillisFixed(1619000000000L);
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertThat(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published"
        )).containsExactlyInAnyOrderElementsOf(Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(60));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertThat(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published",
            "published-2021-04-21T11:13:20.000Z"
        )).containsExactlyInAnyOrderElementsOf(Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(150));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertThat(Sets.newHashSet(
            "published",
            "published-2021-04-21T11:13:20.000Z",
            "published-2021-04-21T12:43:20.000Z"
        )).containsExactlyInAnyOrderElementsOf(Sets.newHashSet(onlineRepositoryBaseDirectory.list()));
    }

    @Test
    public void should_retain_N_most_recent_copies_even_when_older_than_retention_period() throws IOException {
        final int N = 2;

        subject = new FileSystemPublicationObjectPersistence(
            ONLINE_REPOSITORY_BASE_URI, onlineRepositoryBaseDirectory.getAbsolutePath(),
            TA_REPOSITORY_BASE_URI, taRepositoryBaseDirectory.getAbsolutePath(),
            120, N);

        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        DateTimeUtils.setCurrentMillisFixed(1619000000000L);
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertThat(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published"
        )).containsExactlyInAnyOrderElementsOf(Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(150));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertThat(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published",
            "published-2021-04-21T12:43:20.000Z"
        )).containsExactlyInAnyOrderElementsOf(Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(200));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertThat(Sets.newHashSet(
            "published",
            "published-2021-04-21T13:33:20.000Z",
            "published-2021-04-21T12:43:20.000Z"
        )).containsExactlyInAnyOrderElementsOf(Sets.newHashSet(onlineRepositoryBaseDirectory.list()));
    }

    @Test
    public void should_fail_to_write_with_same_timestamp() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/old.cer");
        var publishedObjects = Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS));

        subject.writeAll(publishedObjects);
        assertThatThrownBy(() -> subject.writeAll(publishedObjects))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void should_not_keep_old_objects() {
        URI oldUri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/old.cer");
        URI newUri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/new.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, oldUri, CONTENTS)));
        assertThat(new File(onlineRepositoryBaseDirectory, "published/foo/old.cer")).exists();

        DateTimeUtils.setCurrentMillisFixed(DateTimeUtils.currentTimeMillis() + 100);

        subject.writeAll(Collections.singletonList(new PublishedObjectData(new Timestamp(System.currentTimeMillis()), newUri, CONTENTS)));

        assertThat(new File(onlineRepositoryBaseDirectory, "published/foo/new.cer")).exists();
        assertThat(new File(onlineRepositoryBaseDirectory, "published/foo/old.cer")).doesNotExist();
    }

    @Test
    public void should_reject_uri_outside_of_public_repository() {
        URI uri = URI.create("rsync://somewhere/else/bar.cer");

        var publishedObjects = Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS));
        assertThatThrownBy(() ->subject.writeAll(publishedObjects))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void should_reject_uri_outside_of_public_repository_using_relative_segments() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("../bar.cer");

        var publishedObjects = Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS));
        assertThatThrownBy(() -> subject.writeAll(publishedObjects))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void should_reject_relative_uri() {
        URI uri = URI.create("foo/bar.cer");

        var publishedObjects = Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS));
        assertThatThrownBy(() -> subject.writeAll(publishedObjects))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void cleanup_pattern_should_not_match_published_symlink_name() {
        assertThat(PUBLICATION_DIRECTORY_PATTERN.matcher("published").matches()).isFalse();
    }

    @Test
    public void cleanup_pattern_should_match_target_directory_pattern() {
        assertThat(PUBLICATION_DIRECTORY_PATTERN.matcher("published-2021-04-26T09:57:59.034Z").matches()).isTrue();
    }

    @Test
    public void cleanup_pattern_should_match_temporary_directory_pattern() {
        assertThat(PUBLICATION_DIRECTORY_PATTERN.matcher("tmp-2021-04-26T10:09:06.023Z-4352054854289820810").matches()).isTrue();
    }
}
