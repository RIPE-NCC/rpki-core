package net.ripe.rpki.server.api.configuration;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Properties;

import static net.ripe.rpki.server.api.configuration.RepositoryConfiguration.*;
import static org.junit.Assert.*;

@Ignore
public class RepositoryConfigurationBeanTest {

    private RepositoryConfigurationBean subject;

    private static final String LOCAL_REPO_DIR_NAME = "/tmp/prod";

    @Before
    public void setUp() throws Exception {

        File localRepoDir = new File(LOCAL_REPO_DIR_NAME);
        Files.createDirectories(localRepoDir.toPath());

//        subject = new SharedConfigurationBean("configuration/certification-valid.properties");
    }



    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(new File(LOCAL_REPO_DIR_NAME));
    }

    @Test
    public void shouldAddTrailingSlashToPublicRepositoryUri() {
        Properties props = new Properties();
        props.put(ONLINE_REPOSITORY_BASE_URI, "rsync://localhost/certrepo");
//        subject.configurePublicRepositoryUri(props);
        assertEquals(URI.create("rsync://localhost/certrepo/"), subject.getPublicRepositoryUri());
    }

    @Test(expected=CertificationConfigurationException.class)
    public void shouldRequirePublicRepositoryUriWithRsyncScheme() {
        Properties props = new Properties();
        props.put(ONLINE_REPOSITORY_BASE_URI, "http://localhost/certrepo");
//        subject.configurePublicRepositoryUri(props);
    }

    @Test(expected=CertificationConfigurationException.class)
    public void shouldFailIfPublicRepositoryUriIsInvalid() {
        Properties props = new Properties();
        props.put(ONLINE_REPOSITORY_BASE_URI, "foo bar");
//        subject.configurePublicRepositoryUri(props);
    }

//    @Test(expected=CertificationConfigurationException.class)
//    public void shouldRequireLocalRepositoryDirectory() {
//        subject.validateLocalRepositoryDirectory(new Properties());
//    }
//
//    @Test(expected=CertificationConfigurationException.class)
//    public void shouldRequireExistingLocalRepositoryDirectory() {
//        Properties props = new Properties();
//        props.put(ONLINE_REPOSITORY_BASE_DIRECTORY, "/non-existent-directory");
//        subject.validateLocalRepositoryDirectory(new Properties());
//    }
//
//    @Test(expected=CertificationConfigurationException.class)
//    public void shouldRequirePublicRepositoryUri() {
//        subject.configurePublicRepositoryUri(new Properties());
//    }
//


}
