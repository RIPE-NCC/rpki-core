package net.ripe.rpki.util;

import lombok.Setter;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.rest.service.Rest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Setter
@TestPropertySource(properties = {"management.port="})
@ComponentScan(value = "net.ripe.rpki", lazyInit = false)
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureMetrics
@SpringBootTest(classes = TestRpkiBootApplication.class)
/**
 * Validate that the metrics can be loaded.
 *
 * This instantiates all the beans non-lazily (and thus is slow) and is an integration test.
 */
public class ActuatorMetricsIT {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void prometheusEndpointShouldReturnHttpOk() throws Exception {

        mockMvc.perform(Rest.get("/actuator/prometheus").accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }
}
