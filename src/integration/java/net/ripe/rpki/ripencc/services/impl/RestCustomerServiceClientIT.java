package net.ripe.rpki.ripencc.services.impl;

import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.ripencc.services.impl.CustomerServiceClient.MemberSummary;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestRpkiBootApplication.class)
public class RestCustomerServiceClientIT {

    private static final String KNOWN_MEMBER_REGID = "nl.ripencc-ops";

    @Inject
    private RestCustomerServiceClient subject;

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
