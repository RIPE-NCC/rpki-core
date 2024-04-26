package net.ripe.rpki.ripencc.services.impl;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.MemberResourceResponse;
import net.ripe.rpki.server.api.ports.ResourceServicesClient.MemberResources;
import net.ripe.rpki.server.api.support.objects.CaName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class RestResourceServicesClientIT {

    @Inject
    private RestResourceServicesClient subject;

    @BeforeEach
    public void setUp() {
        Assumptions.assumeTrue(
            serverReachable(),
            "resource service is available"
        );
    }

    private boolean serverReachable() {
        try (Response response = subject.resourcesTarget().request().get()) {
            return response.getStatusInfo().getFamily() != Response.Status.Family.SERVER_ERROR;
        } catch (ProcessingException e) {
            return false;
        }
    }

    @Test
    public void shouldFindAllMemberSummaries() {
        final long membershipId = 1104L;
        final CaName ripeNccTsMemberId = CaName.fromMembershipId(membershipId);

        final MemberResources allResources = subject.fetchAllResources().getAllMembersResources();

        // Now fetch for individual membershipID
        final MemberResourceResponse memberResources = subject.fetchMemberResources(membershipId);

        final Map<CaName, ImmutableResourceSet> certifiableResources = allResources.getCertifiableResources();

        assertThat(certifiableResources).containsKey(ripeNccTsMemberId);

        final ImmutableResourceSet ipResourcesByMemberId = certifiableResources.get(ripeNccTsMemberId);

        final Map<CaName, ImmutableResourceSet> individualMemberResources = memberResources.getResponse().getContent().getCertifiableResources();

        for (CaName caName : certifiableResources.keySet()) {
            if (caName.hasOrganizationId()) {
                if (individualMemberResources.containsKey(caName)) {
                    System.err.println(caName);
                }
            }
        }

        assertThat(ipResourcesByMemberId.contains(individualMemberResources.get(ripeNccTsMemberId))).isTrue();
    }

}
