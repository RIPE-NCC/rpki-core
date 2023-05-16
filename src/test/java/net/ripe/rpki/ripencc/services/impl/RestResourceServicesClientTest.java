package net.ripe.rpki.ripencc.services.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.ripe.rpki.server.api.ports.ResourceServicesClient.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RestResourceServicesClientTest {

    private static final String BASE_URL = "/resource-services/";
    private static final String MEMBER_RESOURCES_URL = "member-resources";
    private static final String TOTAL_RESOURCES_URL = "total-resources";
    private static final String MONITORING_HEALTHCHECK = "monitoring/healthcheck";

    private static final int PORT = 7575;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

    private RestResourceServicesClient subject;

    @Before
    public void setUp() {
        subject = new RestResourceServicesClient("http://localhost:" + PORT + BASE_URL, "");

        stubHttpCallForTheIndexPage();
    }

    private void stubHttpCallForTheIndexPage() {
        stubFor(get(urlEqualTo(BASE_URL ))
                .withHeader("Accept", equalTo(APPLICATION_JSON))
                .willReturn(aResponse()
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody(jsonFromFile("/internet-resources/index_page.json"))));
    }

    @Test
    public void shouldBeAvailable() {
        stubFor(head(urlEqualTo(BASE_URL+ MONITORING_HEALTHCHECK)).withHeader("Accept", equalTo(APPLICATION_JSON)).willReturn(aResponse().withStatus(200)));

        assertTrue(subject.isAvailable());
    }

    @Test
    public void shouldBeUnavailable() {
        stubFor(head(urlEqualTo(BASE_URL+ MONITORING_HEALTHCHECK)).withHeader("Accept", equalTo(APPLICATION_JSON)).willReturn(aResponse().withStatus(500)));

        assertFalse(subject.isAvailable());
    }

    @Test
    public void should_fetch_and_deserialize_all_member_resources() {
        givenTotalResourcesHttpCallWillReturn("/internet-resources/sample_total_resources_response.json");

        final MemberResources expectedMember = new MemberResources(
                Collections.singletonList(new AsnResource(1111L, "AS1111", "ASSIGNED", "1111")),
                Collections.singletonList(new Ipv4Allocation(1111L, "193.1.0.0/21", "ALLOCATED", "1111")),
                Collections.singletonList(new Ipv4Assignment(1111L, "193.6.0.0/21", "ASSIGNED", "ORG-BLUELIGHT")),
                Collections.singletonList(new Ipv6Allocation(1111L, "2001:0:1::/64", "ALLOCATED", "1111")),
                Collections.singletonList(new Ipv6Assignment(1111L, "2001:0:6::/64", "ASSIGNED", "ORG-BLUELIGHT")),
                Collections.singletonList(new Ipv4ErxResource("51.0.0.0/8", "ISSUED", 1111L, "1111"))
        );

        final RipeNccDelegations ripeNccDelegations = new RipeNccDelegations(
            Collections.singletonList(new RipeNccDelegation("AS7-AS7")),
            Arrays.asList(
                new RipeNccDelegation("1.178.112.0/20"),
                new RipeNccDelegation("1.178.128.0/20")
            ),
            Arrays.asList(
                new RipeNccDelegation("2001:600::-2001:7f9:ffff:ffff:ffff:ffff:ffff:ffff"),
                new RipeNccDelegation("2001:7fb::-2001:7ff:ffff:ffff:ffff:ffff:ffff:ffff")
            )
        );

        final TotalResources totalExpected = new TotalResources(expectedMember, ripeNccDelegations);
        assertEquals(totalExpected, subject.fetchAllResources());
    }

    private void givenTotalResourcesHttpCallWillReturn(String pathToJsonFile) {
        stubFor(get(urlEqualTo(BASE_URL + TOTAL_RESOURCES_URL))
                .withHeader("Accept", equalTo(APPLICATION_JSON))
                .willReturn(aResponse()
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody(jsonFromFile(pathToJsonFile))));
    }

    private String jsonFromFile(String pathToJsonFile) {
        return JsonTestUtils.readJsonFile(pathToJsonFile).toString();
    }
}
