package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.MemberResourceResponse;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.MemberResources;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.ws.rs.client.Client;
import java.net.URI;
import java.util.Map;

import static java.lang.String.format;
import static net.ripe.rpki.rest.service.Rest.TESTING_API_KEY;
import static org.junit.Assert.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class RestResourceServicesClientIT {

    private final String internetResourcesUri = "https://rsng-apps.prepdev.ripe.net/resource-services/%s";

    private RestResourceServicesClient subject;
    private Client resource;

    @BeforeEach
    public void setUp() {
        subject = new RestResourceServicesClient(format(internetResourcesUri, ""), true, TESTING_API_KEY);
        resource = subject.getHttpClient();
    }

    @Test
    public void shouldFindAllMemberSummaries() {
        final long membershipId = 1104L;
        final CaName ripeNccTsMemberId = CaName.of(membershipId);

        final MemberResources allResources = subject.fetchAllMemberResources();

        // Now fetch for individual membershipID
        final MemberResourceResponse memberResources = subject.httpGetJson(
            resource.target(URI.create(format(internetResourcesUri, format("member-resources/%d", membershipId)))),
            MemberResourceResponse.class);

        final Map<CaName, IpResourceSet> certifiableResources = allResources.getCertifiableResources();

        assertTrue(certifiableResources.containsKey(ripeNccTsMemberId));

        final IpResourceSet ipResourcesByMemberId = certifiableResources.get(ripeNccTsMemberId);

        final Map<CaName, IpResourceSet> individualMemberResources = memberResources.getResponse().getContent().getCertifiableResources();

        for (CaName caName : certifiableResources.keySet()) {
            if (caName.hasOrganizationId()) {
                if (individualMemberResources.containsKey(caName)) {
                    System.err.println(caName.toString());
                }
            }
        }

        assertTrue(ipResourcesByMemberId.contains(individualMemberResources.get(ripeNccTsMemberId)));
    }

    @Test
    public void shouldFindProductionCaDelegations() {
        final IpResourceSet delegationResult = subject.findProductionCaDelegations();

        assertTrue("No ipv4-delegations found. Should be at least 1 matching", delegationResult.containsType(IpResourceType.IPv4));
        assertTrue("No ipv6-delegations found. Should be at least 1 matching", delegationResult.containsType(IpResourceType.IPv6));
        assertTrue("No asn-delegations found. Should be atleast 1 matching", delegationResult.containsType(IpResourceType.ASN));

    }

}
