package net.ripe.rpki.rest.service.monitoring;

import com.google.common.collect.Lists;
import lombok.Setter;
import lombok.SneakyThrows;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.rest.service.Rest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Setter
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class PublishedObjectsServiceTest {
    @MockitoBean
    private PublishedObjectRepository publishedObjectRepository;

    @Autowired
    private MockMvc mockMvc;

    private final static String BASE_URI = "rsync://localhost/ta/";

    @SneakyThrows
    private List<PublishedObjectEntry>  samplePublishedObjects() {
        final PublishedObjectEntry crt = new PublishedObjectEntry();
        crt.setSha256("f2f8d0d580edfafda2c2c9f8d5b229cf125771040ad2e1a003201e4cc38bd122");
        crt.setUri(BASE_URI + "RIPE-NCC-TEST.cer");

        final PublishedObjectEntry mft = new PublishedObjectEntry();
        mft.setSha256("2ac319bdddc5011d8d0184fdcb785026492af229e4d52b382654b93c7d2512b2");
        mft.setUri(BASE_URI + "RIPE-NCC-TEST.mft");

        return Lists.newArrayList(crt, mft);
    }

    @Test
    public void shouldListPublishedObjects() throws Exception {
        when(publishedObjectRepository.findEntriesByPublicationStatus(PublicationStatus.PUBLISHED_STATUSES)).thenReturn(samplePublishedObjects());

        // Ignoring updatedAt in test
        mockMvc.perform(Rest.get("/api/monitoring/published-objects"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$[0].uri").value(BASE_URI + "RIPE-NCC-TEST.cer"))
                .andExpect(jsonPath("$[0].sha256").value("f2f8d0d580edfafda2c2c9f8d5b229cf125771040ad2e1a003201e4cc38bd122"))
                .andExpect(jsonPath("$[1].uri").value(BASE_URI + "RIPE-NCC-TEST.mft"))
                .andExpect(jsonPath("$[1].sha256").value("2ac319bdddc5011d8d0184fdcb785026492af229e4d52b382654b93c7d2512b2"));
    }

    @Test
    public void databaseShouldAcceptNativeQueryAndReturnArray() throws Exception {
        mockMvc.perform(Rest.get("/api/monitoring/published-objects"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    public void shouldAcceptDepreatedUrl() throws Exception {
        mockMvc.perform(Rest.get("/api/published-objects"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}
