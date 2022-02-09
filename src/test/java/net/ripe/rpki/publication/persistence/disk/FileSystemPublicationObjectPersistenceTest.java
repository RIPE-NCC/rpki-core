package net.ripe.rpki.publication.persistence.disk;

import com.google.common.collect.Sets;
import net.ripe.rpki.commons.util.ConfigurationUtil;
import net.ripe.rpki.domain.PublishedObjectData;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static net.ripe.rpki.publication.persistence.disk.FileSystemPublicationObjectPersistence.PUBLICATION_DIRECTORY_PATTERN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Before
    public void setUp() throws IOException {
        onlineRepositoryBaseDirectory = Files.createTempDirectory("temp-online-repository").toFile();
        taRepositoryBaseDirectory = Files.createTempDirectory("temp-ta-repository").toFile();

        // fix the current time while a test is running
        DateTimeUtils.setCurrentMillisFixed(new DateTime().getMillis());

        subject = new FileSystemPublicationObjectPersistence(
            ONLINE_REPOSITORY_BASE_URI, onlineRepositoryBaseDirectory.toString(),
            TA_REPOSITORY_BASE_URI, taRepositoryBaseDirectory.toString(),
            120, 1);
    }

    @After
    public void tearDown() throws IOException {
        // FileUtils was getting spurious PermissionDeniedExceptions - likely a virus scanner interaction.
        Files.walk(onlineRepositoryBaseDirectory.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        Files.walk(taRepositoryBaseDirectory.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);

        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void should_write_contents_of_publish_request_to_online_repository() throws IOException {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertArrayEquals(CONTENTS, FileUtils.readFileToByteArray(new File(onlineRepositoryBaseDirectory, "published/foo/bar.cer")));
    }

    @Test
    public void should_set_last_modification_time_of_published_object() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertEquals(CREATED_AT.getTime() / 1000, new File(onlineRepositoryBaseDirectory, "published/foo/bar.cer").lastModified() / 1000);
    }

    @Test
    public void should_set_last_modification_time_internal_directories() throws IOException {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/baz/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertEquals(FileSystemPublicationObjectPersistence.INTERNAL_DIRECTORY_LAST_MODIFIED_TIME, Files.getLastModifiedTime(new File(onlineRepositoryBaseDirectory, "published/foo").toPath()));
        assertEquals(FileSystemPublicationObjectPersistence.INTERNAL_DIRECTORY_LAST_MODIFIED_TIME, Files.getLastModifiedTime(new File(onlineRepositoryBaseDirectory, "published/foo/baz").toPath()));
    }

    @Test
    public void should_write_contents_of_publish_request_to_ta_repository() throws IOException {
        URI uri = TA_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertArrayEquals(CONTENTS, FileUtils.readFileToByteArray(new File(taRepositoryBaseDirectory, "published/foo/bar.cer")));
    }

    @Test
    public void should_set_symlink_to_latest_publication_directory() throws IOException {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        DateTimeUtils.setCurrentMillisFixed(1619000000000L);
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        Path targetDirectory = Files.readSymbolicLink(new File(onlineRepositoryBaseDirectory, "published").toPath());
        assertEquals("published-2021-04-21T10:13:20.000Z", targetDirectory.toString());

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(60));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        targetDirectory = Files.readSymbolicLink(new File(onlineRepositoryBaseDirectory, "published").toPath());
        assertEquals("published-2021-04-21T11:13:20.000Z", targetDirectory.toString());
    }

    @Test
    public void should_backup_old_published_directory_when_not_a_symbolic_link() throws IOException {
        // Temporary feature to upgrade existing systems (which have a `published` directory) to symbolic link
        // based publication.
        Path published = onlineRepositoryBaseDirectory.toPath().resolve("published");
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        Files.createDirectories(published);
        assertTrue(Files.isDirectory(published));

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        assertTrue(Files.isSymbolicLink(published));
        assertTrue(Files.isDirectory(published.resolveSibling("published.bak")));
    }

    @Test
    public void should_remove_publication_directories_older_than_retention_period() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/bar.cer");

        DateTimeUtils.setCurrentMillisFixed(1619000000000L);
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertEquals(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published"
        ), Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(60));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertEquals(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published",
            "published-2021-04-21T11:13:20.000Z"
        ), Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(150));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertEquals(Sets.newHashSet(
            "published",
            "published-2021-04-21T11:13:20.000Z",
            "published-2021-04-21T12:43:20.000Z"
        ), Sets.newHashSet(onlineRepositoryBaseDirectory.list()));
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
        assertEquals(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published"
        ), Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(150));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertEquals(Sets.newHashSet(
            "published-2021-04-21T10:13:20.000Z",
            "published",
            "published-2021-04-21T12:43:20.000Z"
        ), Sets.newHashSet(onlineRepositoryBaseDirectory.list()));

        DateTimeUtils.setCurrentMillisFixed(1619000000000L + TimeUnit.MINUTES.toMillis(200));
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
        assertEquals(Sets.newHashSet(
            "published",
            "published-2021-04-21T13:33:20.000Z",
            "published-2021-04-21T12:43:20.000Z"
        ), Sets.newHashSet(onlineRepositoryBaseDirectory.list()));
    }

    @Test
    public void should_fail_to_write_with_same_timestamp() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/old.cer");
        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));

        try {
            subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
            fail("IllegalStateException expected");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void should_not_keep_old_objects() {
        URI oldUri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/old.cer");
        URI newUri = ONLINE_REPOSITORY_BASE_URI.resolve("foo/new.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, oldUri, CONTENTS)));
        assertTrue(new File(onlineRepositoryBaseDirectory, "published/foo/old.cer").exists());

        DateTimeUtils.setCurrentMillisFixed(DateTimeUtils.currentTimeMillis() + 100);

        subject.writeAll(Collections.singletonList(new PublishedObjectData(new Timestamp(System.currentTimeMillis()), newUri, CONTENTS)));

        assertTrue(new File(onlineRepositoryBaseDirectory, "published/foo/new.cer").exists());
        assertFalse(new File(onlineRepositoryBaseDirectory, "published/foo/old.cer").exists());
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_reject_uri_outside_of_public_repository() {
        URI uri = URI.create("rsync://somewhere/else/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_reject_uri_outside_of_public_repository_using_relative_segments() {
        URI uri = ONLINE_REPOSITORY_BASE_URI.resolve("../bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_reject_relative_uri() {
        URI uri = URI.create("foo/bar.cer");

        subject.writeAll(Collections.singletonList(new PublishedObjectData(CREATED_AT, uri, CONTENTS)));
    }

    @Test
    public void cleanup_pattern_should_not_match_published_symlink_name() {
        assertFalse(PUBLICATION_DIRECTORY_PATTERN.matcher("published").matches());
    }

    @Test
    public void cleanup_pattern_should_match_target_directory_pattern() {
        assertTrue(PUBLICATION_DIRECTORY_PATTERN.matcher("published-2021-04-26T09:57:59.034Z").matches());
    }

    @Test
    public void cleanup_pattern_should_match_temporary_directory_pattern() {
        assertTrue(PUBLICATION_DIRECTORY_PATTERN.matcher("tmp-2021-04-26T10:09:06.023Z-4352054854289820810").matches());
    }
}
