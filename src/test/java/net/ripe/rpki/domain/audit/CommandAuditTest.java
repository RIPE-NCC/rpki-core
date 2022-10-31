package net.ripe.rpki.domain.audit;

import net.ripe.rpki.commons.FixedDateRule;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup.*;
import static org.junit.Assert.*;


public class CommandAuditTest {

    @Rule
    public FixedDateRule fixedDateRule = new FixedDateRule(new DateTime(2009, 2, 5, 0, 0, 0, 0, DateTimeZone.UTC).getMillis());

    private static final VersionedId CA_ID = new VersionedId(42L, 2L);
    private static final String PRINCIPAL = "erik";

    private static class TestCommand extends CertificateAuthorityCommand {
        public TestCommand(VersionedId certificateAuthorityId, CertificateAuthorityCommandGroup commandGroup) {
            super(certificateAuthorityId, commandGroup);
        }

        @Override
        public String getCommandSummary() {
            return "Test Command";
        }
    }

    private static final CertificateAuthorityCommand TEST_COMMAND = new TestCommand(CA_ID, USER);

    private CommandAudit subject;


    @Before
    public void setUp() {
        subject = new CommandAudit(PRINCIPAL, TEST_COMMAND.getCertificateAuthorityVersionedId(), TEST_COMMAND, "");
    }

    @Test
    public void shouldStoreCommandExecutionTime() {
        assertEquals(new DateTime(2009, 2, 5, 0, 0, 0, 0, DateTimeZone.UTC), subject.getExecutionTime());
    }

    @Test
    public void shouldStoreCertificateAuthorityIdAndVersion() {
        assertEquals(CA_ID.getId(), subject.getCertificateAuthorityId());
        assertEquals(CA_ID.getVersion(), subject.getCertificateAuthorityVersion());
    }

    @Test
    public void shouldStorePrincipal() {
        assertEquals(PRINCIPAL, subject.getPrincipal());
    }

    @Test
    public void shouldStoreCommandGroup() {
        assertEquals(USER, subject.getCommandGroup());
    }

    @Test
    public void shouldStoreCommandType() {
        assertEquals("TestCommand", subject.getCommandType());
    }

    @Test
    public void shouldStoreCommandSummary() {
        assertEquals(TEST_COMMAND.getCommandSummary(), subject.getCommandSummary());
    }
}
