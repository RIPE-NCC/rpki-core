package net.ripe.rpki.ripencc.services.impl;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.ripencc.services.impl.CustomerServiceClient.MemberSummary;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class RestCustomerServiceClientIT {

    private static final String KNOWN_MEMBER_REGID = "nl.ripencc-ops";

    @Inject
    private RestCustomerServiceClient subject;

    @BeforeEach
    public void setUp() {
        Assumptions.assumeTrue(
            serverReachable(),
            "customer service is available"
        );
    }

    private boolean serverReachable() {
        try (Response response = subject.customerServiceTarget().request().get()) {
            return response.getStatusInfo().getFamily() != Response.Status.Family.SERVER_ERROR;
        } catch (ProcessingException e) {
            return false;
        }
    }

    @Test
    public void should_find_all_member_summaries() {
        List<MemberSummary> memberSummaries = subject.findAllMemberSummaries();

        assertFalse("no members found", memberSummaries.isEmpty());
        boolean containsKnownMember = false;
        for (MemberSummary member: memberSummaries) {
            if (KNOWN_MEMBER_REGID.equals(member.getRegId())) {
                containsKnownMember = true;
                break;
            }
        }
        assertTrue(KNOWN_MEMBER_REGID, containsKnownMember);
    }
}
