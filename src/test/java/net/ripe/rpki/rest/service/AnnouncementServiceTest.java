package net.ripe.rpki.rest.service;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AnnouncedRoute;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.domain.alerts.RoaAlertFrequency;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityType;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaAlertConfigurationData;
import net.ripe.rpki.server.api.dto.RoaAlertSubscriptionData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.RoaAlertConfigurationViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
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

import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.rest.security.ApiKeySecurity.API_KEY_HEADER;
import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static net.ripe.rpki.rest.service.Rest.TESTING_API_KEY;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class AnnouncementServiceTest {

    private static final long CA_ID = 456L;

    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @MockBean
    private BgpRisEntryViewService bgpRisEntryViewService;

    @MockBean
    private RoaViewService roaService;

    @MockBean
    private RoaAlertConfigurationViewService roaAlertConfigurationViewService;

    @MockBean
    private HostedCertificateAuthorityData certificateAuthorityData;

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void init() {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(certificateAuthorityData);
        when(certificateAuthorityData.getId()).thenReturn(CA_ID);
    }

    @Test
    public void announcements_shouldGetAnnouncement() throws Exception {

        final IpResourceSet ipResourceSet = new IpResourceSet();
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 10);
        final BgpRisEntry e2 = new BgpRisEntry(new Asn(20), IpRange.parse("192.168.128.0/24"), 20);
        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(e1));
        bgpRisEntries.put(false, Collections.singletonList(e2));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(getRoaAlertConfigurationData(e1.getOrigin(), e1.getPrefix()));

        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(new ArrayList<>()));

        mockMvc.perform(
                Rest.get(API_URL_PREFIX + "/123/announcements")
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
        )
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value("2"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("10"))
                .andExpect(jsonPath("$.[0].suppressed").value("true"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[0].currentState").value("UNKNOWN"))
                .andExpect(jsonPath("$.[1].verified").value("false"));
    }

    @Test
    public void announcements_shouldIncludeSilencesThatAreNotVisibleInBGP() throws Exception {

        final IpResourceSet ipResourceSet = new IpResourceSet();
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 10);
        final BgpRisEntry e2 = new BgpRisEntry(new Asn(20), IpRange.parse("192.168.128.0/24"), 20);
        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(e1));
        bgpRisEntries.put(false, Collections.singletonList(e2));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        when(roaAlertConfigurationViewService.findRoaAlertSubscription(CA_ID)).thenReturn(
                getRoaAlertConfigurationData(e1.getOrigin(), e1.getPrefix()).withIgnoredAnnouncements(
                        Collections.singleton(new AnnouncedRoute(Asn.parse("AS64496"), IpRange.parse("203.0.113.0/24")))

                )
        );

        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(new ArrayList<>()));

        mockMvc.perform(
                        Rest.get(API_URL_PREFIX + "/123/announcements")
                                .accept(APPLICATION_JSON)
                                .contentType(APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                // The two announcements are still present
                .andExpect(jsonPath("$.length()").value("3"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[1].asn").value("AS20"))
                .andExpect(jsonPath("$.[1].prefix").value("192.168.128.0/24"))
                // And the announcement not in BGP, but suppressed, is present
                .andExpect(jsonPath("$.[2].asn").value("AS64496"))
                .andExpect(jsonPath("$.[2].prefix").value("203.0.113.0/24"))
                .andExpect(jsonPath("$.[2].visibility").value("0"))
                .andExpect(jsonPath("$.[2].suppressed").value("true"))
                .andExpect(jsonPath("$.[2].verified").value("false"))
                .andExpect(jsonPath("$.[2].currentState").value("UNKNOWN"))

        ;
    }

    @Test
    public void shouldGetError404ForNotExistingAnnouncement() throws Exception {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(null);

        mockMvc.perform(
                Rest.get(API_URL_PREFIX + "/123/announcements")
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
        )
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("certificate authority 'CN=123' not found"));
    }

    @Test
    public void shouldGetError403ForbiddenForAbsentUserId() throws Exception {
        mockMvc.perform(
                get(API_URL_PREFIX + "/123/announcements")
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .header(API_KEY_HEADER, TESTING_API_KEY)
        )
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("The cookie 'user-id' is not defined.")));
    }

    @Test
    public void affected_shouldReturnAnnouncementAffectedByROA() throws Exception {

        final IpResourceSet ipResourceSet = new IpResourceSet();
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 10);
        final BgpRisEntry e2 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.128.0/24"), 20);
        final BgpRisEntry e3 = new BgpRisEntry(new Asn(20), IpRange.parse("192.168.128.0/24"), 20);
        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(false, Arrays.asList(e2, e3));
        bgpRisEntries.put(true, Collections.singletonList(e1));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(new ArrayList<>()));

        mockMvc.perform(
                Rest.post(API_URL_PREFIX + "/123/announcements/affected",
                    "{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\": \"16\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value("3"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("10"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"))
                .andExpect(jsonPath("$.[1].asn").value("AS10"))
                .andExpect(jsonPath("$.[1].prefix").value("192.168.128.0/24"))
                .andExpect(jsonPath("$.[1].visibility").value("20"))
                .andExpect(jsonPath("$.[1].suppressed").value("false"))
                .andExpect(jsonPath("$.[1].verified").value("false"))
                .andExpect(jsonPath("$.[1].currentState").value("INVALID_LENGTH"))
                .andExpect(jsonPath("$.[2].asn").value("AS20"))
                .andExpect(jsonPath("$.[2].prefix").value("192.168.128.0/24"))
                .andExpect(jsonPath("$.[2].visibility").value("20"))
                .andExpect(jsonPath("$.[2].suppressed").value("false"))
                .andExpect(jsonPath("$.[2].verified").value("false"))
                .andExpect(jsonPath("$.[2].currentState").value("INVALID_ASN"));
    }

    @Test
    public void affected_shouldReturnAnnouncementAffectedByROAValidatedByOtherROAs() throws Exception {

        final IpResourceSet ipResourceSet = new IpResourceSet();
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 10);
        final BgpRisEntry e2 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.128.0/24"), 20);
        final BgpRisEntry e3 = new BgpRisEntry(new Asn(20), IpRange.parse("192.168.128.0/24"), 20);
        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(e1));
        bgpRisEntries.put(false, Arrays.asList(e2, e3));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.128.0/24"), 24)
        )));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/announcements/affected",
                "{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\": \"16\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value("2"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("10"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"))
                .andExpect(jsonPath("$.[1].asn").value("AS20"))
                .andExpect(jsonPath("$.[1].prefix").value("192.168.128.0/24"))
                .andExpect(jsonPath("$.[1].visibility").value("20"))
                .andExpect(jsonPath("$.[1].suppressed").value("false"))
                .andExpect(jsonPath("$.[1].currentState").value("INVALID_ASN"));
    }


    @Test
    public void affected_shouldNotReturnUnknownAnnouncements() throws Exception {

        final IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("192.168.0.0/16"));
        when(certificateAuthorityData.getResources()).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(10), IpRange.parse("191.168.0.0/16"), 10);
        final BgpRisEntry e2 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 10);
        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(e2));
        bgpRisEntries.put(false, Collections.singletonList(e1));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        when(roaService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(new ArrayList<>()));


        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/announcements/affected",
                "{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\": \"16\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("10"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"));
    }

    private RoaAlertConfigurationData getRoaAlertConfigurationData(Asn asn, IpRange range) {
        final CertificateAuthorityData caData = new ManagedCertificateAuthorityData(new VersionedId(CA_ID, 1L),
            new X500Principal("CN=zz.example"), UUID.randomUUID(), 1L, CertificateAuthorityType.HOSTED,
            IpResourceSet.ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

        final Set<AnnouncedRoute> ignoredAnnouncements = new HashSet<>(1);
        ignoredAnnouncements.add(new AnnouncedRoute(asn, range));

        final List<RouteValidityState> routeValidityStates = Arrays.asList(RouteValidityState.INVALID_ASN, RouteValidityState.INVALID_LENGTH, RouteValidityState.UNKNOWN);
        final RoaAlertSubscriptionData subscription = new RoaAlertSubscriptionData("joe@example.com",
                routeValidityStates, RoaAlertFrequency.DAILY);

        return new RoaAlertConfigurationData(caData, subscription, ignoredAnnouncements);
    }

}
