package net.ripe.rpki.ripencc.ui.daemon.health;

import net.ripe.rpki.TestRpkiBootApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class HealthChecksTest {

    @Inject
    HealthChecks subject;

    @Test
    public void checkRegistered() {
            assertThat(subject.getChecks().size()).isEqualTo(5);
    }
}
