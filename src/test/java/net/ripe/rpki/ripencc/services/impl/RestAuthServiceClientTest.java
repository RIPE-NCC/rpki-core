package net.ripe.rpki.ripencc.services.impl;

import net.ripe.rpki.TestRpkiBootApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static net.ripe.rpki.rest.service.Rest.TESTING_API_KEY;
import static org.junit.Assert.assertFalse;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class RestAuthServiceClientTest {

    @Test
    public void should_be_false_if_path_is_wrong() {
        AuthServiceClient subject = new RestAuthServiceClient("http://ba-apps.ripe.net/some/wrong/path", 1000, 1000, TESTING_API_KEY);
        assertFalse(subject.isAvailable());
    }

    @Test
    public void should_be_false_if_host_is_wrong() {
        AuthServiceClient subject = new RestAuthServiceClient("http://1.2.3.4.5/account-service/accounts/sso", 1000, 1000, TESTING_API_KEY);
        assertFalse(subject.isAvailable());
    }
}
