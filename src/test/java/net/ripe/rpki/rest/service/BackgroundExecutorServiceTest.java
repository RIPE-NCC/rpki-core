package net.ripe.rpki.rest.service;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.services.impl.background.AllCaCertificateUpdateServiceBean;
import net.ripe.rpki.services.impl.background.BackgroundServices;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;
import jakarta.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;
import static net.ripe.rpki.rest.security.ApiKeySecurity.USER_ID_HEADER;
import static net.ripe.rpki.rest.service.Rest.TESTING_API_KEY;
import static net.ripe.rpki.server.api.services.background.BackgroundService.BATCH_SIZE_PARAMETER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @MockitoBean
    private BackgroundServices backgroundServices;

    @MockitoBean
    private AllCaCertificateUpdateServiceBean backgroundService;

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void before() {
        when(backgroundServices.getByName(BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE)).thenReturn(backgroundService);
        when(backgroundService.getName()).thenReturn("dummyService");
        when(backgroundService.getStatus()).thenReturn("not running");
        when(backgroundService.supportedParameters()).thenReturn(Collections.singletonMap(BATCH_SIZE_PARAMETER, "1000"));
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
    @SuppressWarnings("unchecked")
    public void postExecutesTheService() throws Exception {
        mockMvc.perform(post("/api/background/service/allCertificateUpdateService?batchSize=100")
                .header(API_KEY_HEADER, TESTING_API_KEY)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .cookie(new Cookie(USER_ID_HEADER, UUID.randomUUID().toString()))
        )
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("dummyService has been triggered through REST API")));

        ArgumentCaptor<Map<String, String>> parametersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(backgroundServices).trigger(eq(BackgroundServices.ALL_CA_CERTIFICATE_UPDATE_SERVICE), parametersCaptor.capture());
        assertThat(parametersCaptor.getValue())
            .hasSize(1)
            .containsEntry(BATCH_SIZE_PARAMETER, "100");
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

        verify(backgroundServices, never()).trigger(anyString(), anyMap());
    }

    @Test
    public void postShouldRejectUnknownOrBadParameters() throws Exception {
        mockMvc.perform(post("/api/background/service/allCertificateUpdateService?foo=bar&batchSize=@$")
                .header(API_KEY_HEADER, TESTING_API_KEY)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .cookie(new Cookie(USER_ID_HEADER, UUID.randomUUID().toString()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("incorrect job parameter(s) - [foo=bar, batchSize=@$]")));

        verify(backgroundServices, never()).trigger(anyString(), anyMap());
    }

    @Test
    public void postShouldRejectTooManyParameters() throws Exception {
        mockMvc.perform(post("/api/background/service/allCertificateUpdateService?1=1&2=2&3=3&4=4&5=5&6=6&7=7&8=8&9=9&10=10&11=11")
                .header(API_KEY_HEADER, TESTING_API_KEY)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .cookie(new Cookie(USER_ID_HEADER, UUID.randomUUID().toString()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().string(startsWith("too many job parameters")));

        verify(backgroundServices, never()).trigger(anyString(), anyMap());
    }
}
