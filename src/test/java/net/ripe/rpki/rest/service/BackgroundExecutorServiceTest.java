package net.ripe.rpki.rest.service;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.server.api.services.background.BackgroundService;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.Cookie;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;
import static net.ripe.rpki.rest.security.ApiKeySecurity.USER_ID_HEADER;
import static net.ripe.rpki.rest.service.Rest.TESTING_API_KEY;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class BackgroundExecutorServiceTest {

    @MockBean(classes = AllCaCertificateUpdateServiceBean.class)
    private BackgroundService backgroundService;

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void before() {
        when(backgroundService.getName()).thenReturn("dummyService");
        when(backgroundService.getStatus()).thenReturn("not running");
    }

    @Test
    public void getShouldFailWhenInvalidService() throws Exception {
        mockMvc.perform(Rest.get("/api/background/service/dummy"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().string("service missing or invalid - dummy"));
    }

    @Test
    public void testGetBackgroundServiceStatus() throws Exception {
        mockMvc.perform(Rest.get("/api/background/service/allCertificateUpdateService"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().string("allCertificateUpdateService is not running"));

        when(backgroundService.getStatus()).thenReturn("running");

        mockMvc.perform(Rest.get("/api/background/service/allCertificateUpdateService"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().string("allCertificateUpdateService is running"));
    }

    @Test
    public void postShouldFailWhenInvalidService() throws Exception {
        mockMvc.perform(post("/api/background/service/dummy")
                .header(API_KEY_HEADER, TESTING_API_KEY)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .cookie(new Cookie(USER_ID_HEADER, UUID.randomUUID().toString()))
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().string("service missing or invalid - dummy"));
    }

    @Test
    public void postExecutesTheService() throws Exception {
        mockMvc.perform(post("/api/background/service/allCertificateUpdateService")
                .header(API_KEY_HEADER, TESTING_API_KEY)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .cookie(new Cookie(USER_ID_HEADER, UUID.randomUUID().toString()))
        )
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("dummyService has been executed through REST API")));

        verify(backgroundService, times(1)).execute();
    }

    @Test
    public void postShouldFailWhenServiceRunning() throws Exception {
        when(backgroundService.isWaitingOrRunning()).thenReturn(true);

        mockMvc.perform(post("/api/background/service/allCertificateUpdateService")
                .header(API_KEY_HEADER, TESTING_API_KEY)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .cookie(new Cookie(USER_ID_HEADER, UUID.randomUUID().toString()))
        )
                .andExpect(status().is(412))
                .andExpect(content().string(startsWith("allCertificateUpdateService is already waiting or running")));

        verify(backgroundService, never()).execute();
    }
}