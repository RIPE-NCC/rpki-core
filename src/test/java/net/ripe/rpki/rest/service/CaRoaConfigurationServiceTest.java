package net.ripe.rpki.rest.service;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.commons.validation.roa.AllowedRoute;
import net.ripe.rpki.commons.validation.roa.RouteValidityState;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.BgpRisEntry;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.RoaConfigurationData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.RoaConfigurationForPrivateASNException;
import net.ripe.rpki.server.api.services.read.BgpRisEntryViewService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.read.ResourceCertificateViewService;
import net.ripe.rpki.server.api.services.read.RoaViewService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.ripe.rpki.rest.service.AbstractCaRestService.API_URL_PREFIX;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class CaRoaConfigurationServiceTest {
    private static final long CA_ID = 123L;
    public static final String TESTNET_1 = "192.0.2.0/24";
    public static final String TESTNET_2 = "198.51.100.0/24";

    @MockBean
    private RoaViewService roaViewService;

    @MockBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    @MockBean
    private ResourceCertificateViewService resourceCertificateViewService;

    @MockBean
    private BgpRisEntryViewService bgpRisEntryViewService;

    @MockBean
    private CommandService commandService;

    private CertificateAuthorityData certificateAuthorityData = mock(CertificateAuthorityData.class);

    @Autowired
    private MockMvc mockMvc;

    @Before
    public void init() {
        reset(certificateAuthorityViewService, roaViewService, resourceCertificateViewService, bgpRisEntryViewService, commandService);
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(certificateAuthorityData);
        when(certificateAuthorityData.getId()).thenReturn(CA_ID);
    }

    @Test
    public void shouldPostROAtoStageNoRealChange() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/16"), 16))));

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(bgpRisEntryViewService.findMostSpecificOverlapping(ipResourceSet)).thenReturn(
                Collections.singletonList(new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 16)));

        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 16)));

        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                .content("[{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\" : \"16\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("16"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"))
                .andExpect(jsonPath("$.[0].futureState").value("VALID"));
    }

    @Test
    public void shouldPostROAtoStageBreakLength() throws Exception {
        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse(TESTNET_1), 32))));

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        // first /28 in TESTNET_1
        when(bgpRisEntryViewService.findMostSpecificOverlapping(ipResourceSet)).thenReturn(
                Collections.singletonList(new BgpRisEntry(new Asn(10), IpRange.parse("192.0.2.0/28"), 1000)));

        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(new BgpRisEntry(new Asn(10), IpRange.parse("192.0.2.0/28"), 1000)));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                .content("[{\"asn\" : \"AS10\", \"prefix\" : \"" + TESTNET_1 + "\", \"maximalLength\" : \"24\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.0.2.0/28"))
                .andExpect(jsonPath("$.[0].visibility").value("1000"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"))
                .andExpect(jsonPath("$.[0].futureState").value("INVALID_LENGTH"));
    }

    /**
     * A ROA exists for TESTNET-1. Then the set of ROAs is modified to just have a ROA for TESTNET-2.
     *
     * TESTNET-1 transitions from VALID to UNKNOWN.
     */
    @Test
    public void shouldPostROAtoStageValidToUnknownPrefix() throws Exception {
        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse(TESTNET_1), 24))));

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(new BgpRisEntry(new Asn(10), IpRange.parse(TESTNET_1), 1000)));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                .content("[{\"asn\" : \"AS10\", \"prefix\" : \"" + TESTNET_2 +"\", \"maximalLength\" : \"24\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value(TESTNET_1))
                .andExpect(jsonPath("$.[0].visibility").value("1000"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"))
                .andExpect(jsonPath("$.[0].futureState").value("UNKNOWN"));
    }

    @Test
    public void shouldPostROAtoStageRemoveROA() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Arrays.asList(
                new RoaConfigurationPrefixData(new Asn(11), IpRange.parse("192.168.0.0/16"), 16),
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("193.0.24.0/21"), 21))));

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(new BgpRisEntry(new Asn(11), IpRange.parse("192.168.0.0/16"), 16)));
        bgpRisEntries.put(false, Collections.singletonList(new BgpRisEntry(new Asn(10), IpRange.parse("193.0.24.0/21"), 21)));

        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                .content("[{\"asn\" : \"AS11\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\" : \"16\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("2"))
                .andExpect(jsonPath("$.[0].asn").value("AS11"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("16"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"))
                .andExpect(jsonPath("$.[0].futureState").value("VALID"))
                .andExpect(jsonPath("$.[0].affectedByChange").value("false"))
                .andExpect(jsonPath("$.[1].asn").value("AS10"))
                .andExpect(jsonPath("$.[1].prefix").value("193.0.24.0/21"))
                .andExpect(jsonPath("$.[1].visibility").value("21"))
                .andExpect(jsonPath("$.[1].suppressed").value("false"))
                .andExpect(jsonPath("$.[1].currentState").value("VALID"))
                .andExpect(jsonPath("$.[1].futureState").value("UNKNOWN"))
                .andExpect(jsonPath("$.[1].affectedByChange").value("true"))
                .andExpect(jsonPath("$.[1].verified").value("false"));
    }

    @Test
    public void shouldPostROAtoStageModifyROA() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Arrays.asList(
                new RoaConfigurationPrefixData(new Asn(11), IpRange.parse("192.168.0.0/16"), 16),
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("193.0.24.0/21"), 21))));

        IpResourceSet ipResourceSet = new IpResourceSet(Ipv4Address.parse("127.0.0.1"), Ipv6Address.parse("::1"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        Map<Boolean, Collection<BgpRisEntry>> bgpRisEntries = new HashMap<>();
        bgpRisEntries.put(true, Collections.singletonList(new BgpRisEntry(new Asn(11), IpRange.parse("192.168.0.0/16"), 16)));
        bgpRisEntries.put(false, Collections.singletonList(new BgpRisEntry(new Asn(10), IpRange.parse("193.0.24.0/21"), 21)));
        when(bgpRisEntryViewService.findMostSpecificContainedAndNotContained(ipResourceSet)).thenReturn(bgpRisEntries);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                .content("[{\"asn\" : \"AS11\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\" : \"16\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("2"))
                .andExpect(jsonPath("$.[0].asn").value("AS11"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].visibility").value("16"))
                .andExpect(jsonPath("$.[0].suppressed").value("false"))
                .andExpect(jsonPath("$.[0].currentState").value("VALID"))
                .andExpect(jsonPath("$.[0].futureState").value("VALID"))
                .andExpect(jsonPath("$.[0].affectedByChange").value("false"))
                .andExpect(jsonPath("$.[0].verified").value("true"))
                .andExpect(jsonPath("$.[1].asn").value("AS10"))
                .andExpect(jsonPath("$.[1].prefix").value("193.0.24.0/21"))
                .andExpect(jsonPath("$.[1].visibility").value("21"))
                .andExpect(jsonPath("$.[1].suppressed").value("false"))
                .andExpect(jsonPath("$.[1].currentState").value("VALID"))
                .andExpect(jsonPath("$.[1].futureState").value("UNKNOWN"))
                .andExpect(jsonPath("$.[1].affectedByChange").value("true"))
                .andExpect(jsonPath("$.[1].verified").value("false"));
    }

    @Test
    public void shouldRejectInvalidROAWhenStaging() throws Exception {
        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                .content("[{\"prefix\" : \"wrong prefix\", \"maximalLength\" : \"16\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.error").value("New ROAs are not correct: " +
                        "ASN is empty in (ROA{asn='null', prefix='wrong prefix', maximalLength=16}), " +
                        "Prefix 'wrong prefix' is invalid in (ROA{asn='null', prefix='wrong prefix', maximalLength=16})"));
    }

    @Test
    public void shouldRejectMissingMaxLengthROAWhenStaging() throws Exception {
        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                .content("[{\"asn\" : \"AS11\", \"prefix\" : \"192.168.0.0/16\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.error").value("New ROAs are not correct: " +
                        "Max length must be at most /32 (IPv4) or /128 (IPv6) but was null"));
    }

    // We do not really case about how it is rejected, just that it is rejected.
    @Test
    public void shouldRejectROAWithMaxLengthOverflowingIntWhenStaging() throws Exception {
        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/stage")
                        .content("[{\"asn\" : \"AS11\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\": 8589934592}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void shouldReturnAffectingROAsAllIsFine() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/16"), 16))));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/affecting")
                .content("{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].roa.asn").value("AS10"))
                .andExpect(jsonPath("$.[0].roa.prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].roa.maximalLength").value("16"))
                .andExpect(jsonPath("$.[0].validity").value("VALID"));
    }

    @Test
    public void shouldReturnAffectingROAsLargerROA() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/15"), 16))));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/affecting")
                .content("{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].roa.asn").value("AS10"))
                .andExpect(jsonPath("$.[0].roa.prefix").value("192.168.0.0/15"))
                .andExpect(jsonPath("$.[0].roa.maximalLength").value("16"))
                .andExpect(jsonPath("$.[0].validity").value("VALID"));
    }

    @Test
    public void shouldReturnAffectingROAsInvalidLength() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/15"), 15))));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/affecting")
                .content("{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].roa.asn").value("AS10"))
                .andExpect(jsonPath("$.[0].roa.prefix").value("192.168.0.0/15"))
                .andExpect(jsonPath("$.[0].roa.maximalLength").value("15"))
                .andExpect(jsonPath("$.[0].validity").value("INVALID_LENGTH"));
    }

    @Test
    public void shouldReturnAffectingROAsInvalidASN() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(11), IpRange.parse("192.168.0.0/16"), 16))));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/affecting")
                .content("{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0].roa.asn").value("AS11"))
                .andExpect(jsonPath("$.[0].roa.prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].roa.maximalLength").value("16"))
                .andExpect(jsonPath("$.[0].validity").value("INVALID_ASN"));
    }

    @Test
    public void shouldReturnAffectingROAsAllTogether() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Arrays.asList(
                new RoaConfigurationPrefixData(new Asn(11), IpRange.parse("192.168.0.0/16"), 16),
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/15"), 15),
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/16"), 16))));
//
//        baseRequest().request()
//                .contentType(APPLICATION_JSON)
//                .body("{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}")
//                .then().expect().statusCode(200).and()
//                .body("size()", equalTo(3))
//                .when().post(BASE_PATH + "/ca/123/roas/affecting");

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/affecting")
                .content("{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("3"));
    }

    @Test
    public void shouldPublishRoas() throws Exception {

        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("193.0.24.0/21"), IpRange.parse("2001:67c:64::/48"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(certificateAuthorityData.getVersionedId()).thenReturn(VersionedId.parse("1"));

        ArgumentCaptor<UpdateRoaConfigurationCommand> argument = ArgumentCaptor.forClass(UpdateRoaConfigurationCommand.class);

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                .content("{ " +
                        "\"added\" : [{\"asn\" : \"AS10\", \"prefix\" : \"193.0.24.0/21\", \"maximalLength\" : \"21\"}], " +
                        "\"deleted\" : [{\"asn\" : \"AS11\", \"prefix\" : \"2001:67c:64::/48\", \"maximalLength\" : \"48\"}] " +
                        "}"))
                .andExpect(status().is(204));

        verify(commandService).execute(argument.capture());

        RoaConfigurationPrefixData added = argument.getValue().getAdditions().get(0);
        RoaConfigurationPrefixData deleted = argument.getValue().getDeletions().get(0);

        assertEquals("AS10", added.getAsn().toString());
        assertEquals("193.0.24.0/21", added.getPrefix().toString());
        assertEquals(21, added.getMaximumLength());

        assertEquals("AS11", deleted.getAsn().toString());
        assertEquals("2001:67c:64::/48", deleted.getPrefix().toString());
        assertEquals(48, deleted.getMaximumLength());
    }

    @Test
    public void shouldNotAddRoasIfCaIsNotTheOwner() throws Exception {
        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("193.0.24.0/21"), IpRange.parse("2001:67c:64::/48"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(certificateAuthorityData.getVersionedId()).thenReturn(VersionedId.parse("1"));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                .content("{ \"added\" : [{\"asn\" : \"AS10\", \"prefix\" : \"192.168.0.0/16\", \"maximalLength\" : \"16\"}] }"))
                .andExpect(status().is(400));
    }

    @Test
    public void shouldNotAddRoasIfPrefixIsInvalid() throws Exception {
        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("193.0.24.0/21"), IpRange.parse("2001:67c:64::/48"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(certificateAuthorityData.getVersionedId()).thenReturn(VersionedId.parse("1"));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                .content("{ \"added\" : [{\"asn\" : \"AS10\", \"prefix\" : \"211111.168.0.0/16\", \"maximalLength\" : \"16\"}] }"))
                .andExpect(status().is(400));

//        baseRequest().request()
//                .contentType(APPLICATION_JSON)
//                .body("{ \"added\" : [{\"asn\" : \"AS10\", \"prefix\" : \"211111.168.0.0/16\", \"maximalLength\" : \"16\"}] }")
//                .then().expect().statusCode(400)
//                .when().post(BASE_PATH + "/ca/123/roas/publish");
    }

    @Test
    public void shouldNotAddRoasIfMissingMaxLength() throws Exception {
        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("192.0.2.0/24"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(certificateAuthorityData.getVersionedId()).thenReturn(VersionedId.parse("1"));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                        .content("{ \"added\" : [{\"asn\" : \"AS10\", \"prefix\" : \"192.0.2.0/24\"}] }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void shouldNotAddPrivateRoas() throws Exception {
        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("193.0.24.0/21"), IpRange.parse("2001:67c:64::/48"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(certificateAuthorityData.getVersionedId()).thenReturn(VersionedId.parse("1"));
        when(commandService.execute(isA(UpdateRoaConfigurationCommand.class)))
                .thenThrow(new RoaConfigurationForPrivateASNException(Collections.singletonList(new Asn(64512l))));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                .content("{ \"added\" : [{\"asn\" : \"AS64512\", \"prefix\" : \"193.0.24.0/21\", \"maximalLength\" : " + "\"21\"}] }"))
                .andExpect(status().is(400));

    }

    @Test
    public void shouldNotDeleteRoasIfPrefixIsInvalid() throws Exception {
        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("193.0.24.0/21"), IpRange.parse("2001:67c:64::/48"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(certificateAuthorityData.getVersionedId()).thenReturn(VersionedId.parse("1"));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                .content("{ \"deleted\" : [{\"asn\" : \"AS11\", \"prefix\" : \"2111111.0.0.99/48\", \"maximalLength\" : \"48\"}]}"))
                .andExpect(status().is(400));
    }

    @Test
    public void shouldNotPublishIfMaximalLengthIsTooBig() throws Exception {
        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("193.0.24.0/21"), IpRange.parse("2001:67c:64::/48"));
        when(resourceCertificateViewService.findCertifiedResources(CA_ID)).thenReturn(ipResourceSet);

        when(certificateAuthorityData.getVersionedId()).thenReturn(VersionedId.parse("1"));

        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                .content("{ \"added\" : [{\"asn\" : \"AS11\", \"prefix\" : \"193.0.24.0/21\", \"maximalLength\" : \"48\"}]}"))
                .andExpect(status().is(400))
                .andExpect(jsonPath("$.error").value("Added ROAs are incorrect: Max length must be at most /32 (IPv4) or /128 (IPv6) but was 48"));
    }

    @Test
    public void shouldRejectInvalidROAWhenPublishing() throws Exception {
        mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/roas/publish")
                .content("{ \"added\" : [{\"prefix\" : \"wrong prefix\", \"maximalLength\" : \"16\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.error").value("Added ROAs are incorrect: " +
                        "ASN is empty in (ROA{asn='null', prefix='wrong prefix', maximalLength=16}), " +
                        "Prefix 'wrong prefix' is invalid in (ROA{asn='null', prefix='wrong prefix', maximalLength=16})"));
    }

    @Test
    public void shouldReturnOneBreakingROAs() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/16"), 16))));

        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("192.168.0.0/16"));
        when(resourceCertificateViewService.findCertifiedResources(123L)).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(20), IpRange.parse("192.168.0.0/16"), 25);
        when(bgpRisEntryViewService.findMostSpecificOverlapping(new IpResourceSet(IpRange.parse("192.168.0.0/16")))).thenReturn(Collections.singletonList(e1));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/roas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0]._numberOfInvalidsCaused").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].maximalLength").value("16"));
    }

    @Test
    public void shouldReturnOneValidROAs() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Collections.singletonList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/16"), 16))));

        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("192.168.0.0/16"));
        when(resourceCertificateViewService.findCertifiedResources(123L)).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(10), IpRange.parse("192.168.0.0/16"), 25);
        when(bgpRisEntryViewService.findMostSpecificOverlapping(new IpResourceSet(IpRange.parse("192.168.0.0/16")))).thenReturn(Collections.singletonList(e1));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/roas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("1"))
                .andExpect(jsonPath("$.[0]._numberOfValidsCaused").value("1"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].maximalLength").value("16"));
    }

    @Test
    public void shouldReturnBreakingROAsInCaseThereIsAValidatingROA() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Arrays.asList(
                new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/16"), 16),
                new RoaConfigurationPrefixData(new Asn(20), IpRange.parse("192.168.0.0/16"), 16))
        ));

        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("192.168.0.0/16"));
        when(resourceCertificateViewService.findCertifiedResources(123L)).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(20), IpRange.parse("192.168.0.0/16"), 25);
        when(bgpRisEntryViewService.findMostSpecificOverlapping(new IpResourceSet(IpRange.parse("192.168.0.0/16")))).thenReturn(Collections.singletonList(e1));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/roas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("2"))
                .andExpect(jsonPath("$.[0]._numberOfInvalidsCaused").value("0"))
                .andExpect(jsonPath("$.[0]._numberOfValidsCaused").value("0"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].maximalLength").value("16"))
                .andExpect(jsonPath("$.[1]._numberOfInvalidsCaused").value("0"))
                .andExpect(jsonPath("$.[1]._numberOfValidsCaused").value("1"))
                .andExpect(jsonPath("$.[1].asn").value("AS20"))
                .andExpect(jsonPath("$.[1].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[1].maximalLength").value("16"));
    }

    @Test
    public void shouldReturnBreakingROAsValidAndInvalid() throws Exception {

        when(roaViewService.getRoaConfiguration(CA_ID)).thenReturn(new RoaConfigurationData(Arrays.asList(
                        new RoaConfigurationPrefixData(new Asn(10), IpRange.parse("192.168.0.0/16"), 16),
                        new RoaConfigurationPrefixData(new Asn(30), IpRange.parse("192.168.128.0/24"), 24)))
        );

        IpResourceSet ipResourceSet = new IpResourceSet(IpRange.parse("192.168.0.0/16"), IpRange.parse("192.168.128.0/24"));
        when(resourceCertificateViewService.findCertifiedResources(123L)).thenReturn(ipResourceSet);

        final BgpRisEntry e1 = new BgpRisEntry(new Asn(20), IpRange.parse("192.168.0.0/16"), 25);
        final BgpRisEntry e2 = new BgpRisEntry(new Asn(30), IpRange.parse("192.168.128.0/24"), 35);
        when(bgpRisEntryViewService.findMostSpecificOverlapping(new IpResourceSet(IpRange.parse("192.168.0.0/16")))).thenReturn(Collections.singletonList(e1));
        when(bgpRisEntryViewService.findMostSpecificOverlapping(new IpResourceSet(IpRange.parse("192.168.128.0/24")))).thenReturn(Collections.singletonList(e2));
        when(bgpRisEntryViewService.findMostSpecificOverlapping(ipResourceSet)).thenReturn(Arrays.asList(e1, e2));

        mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/roas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value("2"))
                .andExpect(jsonPath("$.[0].asn").value("AS10"))
                .andExpect(jsonPath("$.[0].prefix").value("192.168.0.0/16"))
                .andExpect(jsonPath("$.[0].maximalLength").value("16"))
                .andExpect(jsonPath("$.[0]._numberOfInvalidsCaused").value("1"))
                .andExpect(jsonPath("$.[0]._numberOfValidsCaused").value("0"))
                .andExpect(jsonPath("$.[1].asn").value("AS30"))
                .andExpect(jsonPath("$.[1].prefix").value("192.168.128.0/24"))
                .andExpect(jsonPath("$.[1].maximalLength").value("24"))
                .andExpect(jsonPath("$.[1]._numberOfInvalidsCaused").value("0"))
                .andExpect(jsonPath("$.[1]._numberOfValidsCaused").value("1"));
    }

    @Test
    public void testDetermineValidityState() {
        final IpRange testNet1 = IpRange.parse("192.0.2.0/24");
        final IpRange testNet2 = IpRange.parse("198.51.100.0/24");
        final String reservedAs = "64496";

        final AllowedRoute ALLOWED_ROUTE = new AllowedRoute(Asn.parse(reservedAs), testNet1, 25);

        // Accept matching route as well as more specific within maximum length
        then(CaRoaConfigurationService.determineValidityState(testNet1, reservedAs, ALLOWED_ROUTE)).isEqualTo(RouteValidityState.VALID);
        then(CaRoaConfigurationService.determineValidityState(IpRange.parse("192.0.2.0/25"), reservedAs, ALLOWED_ROUTE)).isEqualTo(RouteValidityState.VALID);
        // Also with 'AS' prefix - the parser is lenient
        then(CaRoaConfigurationService.determineValidityState(testNet1, "AS" + reservedAs, ALLOWED_ROUTE)).isEqualTo(RouteValidityState.VALID);
        then(CaRoaConfigurationService.determineValidityState(testNet1, "as" + reservedAs, ALLOWED_ROUTE)).isEqualTo(RouteValidityState.VALID);
        then(CaRoaConfigurationService.determineValidityState(testNet1, "As" + reservedAs, ALLOWED_ROUTE)).isEqualTo(RouteValidityState.VALID);
        // Reject wrong AS
        then(CaRoaConfigurationService.determineValidityState(testNet1, "64497", ALLOWED_ROUTE)).isEqualTo(RouteValidityState.INVALID_ASN);
        then(CaRoaConfigurationService.determineValidityState(testNet1, "0", ALLOWED_ROUTE)).isEqualTo(RouteValidityState.INVALID_ASN);
        // Reject wrong length
        then(CaRoaConfigurationService.determineValidityState(IpRange.parse("192.0.2.0/26"), reservedAs, ALLOWED_ROUTE)).isEqualTo(RouteValidityState.INVALID_LENGTH);
        // Reject wrong prefix as input
        thenThrownBy(() -> CaRoaConfigurationService.determineValidityState(testNet2, reservedAs, ALLOWED_ROUTE)).isInstanceOf(IllegalArgumentException.class);

        // And reject inputs that are not an AS
        thenThrownBy(() -> CaRoaConfigurationService.determineValidityState(testNet1, "not_an_as", ALLOWED_ROUTE)).isInstanceOf(IllegalArgumentException.class);
        thenThrownBy(() -> CaRoaConfigurationService.determineValidityState(testNet1, "-1", ALLOWED_ROUTE)).isInstanceOf(IllegalArgumentException.class);
    }
}
