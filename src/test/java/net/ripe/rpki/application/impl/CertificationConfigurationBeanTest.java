package net.ripe.rpki.application.impl;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.net.URI;

import static org.junit.Assert.assertEquals;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class CertificationConfigurationBeanTest {

    private static final String LOCAL_REPO_DIR_NAME = "/tmp/online";

    @Autowired
    private CertificationConfiguration certificationConfiguration;
    @Autowired
    private RepositoryConfiguration repositoryConfiguration;

    @Test
    public void shouldLoadCertificationProperties() throws Exception {
        assertEquals(new File(LOCAL_REPO_DIR_NAME), repositoryConfiguration.getLocalRepositoryDirectory());
        assertEquals(new URI("rsync://localhost/online/"), repositoryConfiguration.getPublicRepositoryUri());

        assertEquals("CN=RIPE NCC Resources,O=RIPE NCC,C=NL", repositoryConfiguration.getProductionCaPrincipal().getName());
        assertEquals(365, certificationConfiguration.getAutoKeyRolloverMaxAgeDays());
        assertEquals(Duration.standardHours(24), certificationConfiguration.getStagingPeriod());
        assertEquals("/tmp", certificationConfiguration.getKeyManagementDataDirectory());
        assertEquals("/tmp", certificationConfiguration.getKeyManagementDataArchiveDirectory());
    }

}
