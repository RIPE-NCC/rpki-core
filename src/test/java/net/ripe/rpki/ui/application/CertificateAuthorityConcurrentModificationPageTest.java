package net.ripe.rpki.ui.application;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.commands.UpdateRoaConfigurationCommand;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.dto.RoaConfigurationPrefixData;
import net.ripe.rpki.server.api.services.command.CertificateAuthorityConcurrentModificationException;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.easymock.EasyMock.expect;

public class CertificateAuthorityConcurrentModificationPageTest extends CertificationWicketTestCase {

    @Test
    public void shouldRender() {
        List<CommandAuditData> commands = createSingleKeyPairCommandList();
        CertificateAuthorityConcurrentModificationException exception = new CertificateAuthorityConcurrentModificationException(new UpdateAllIncomingResourceCertificatesCommand(PRODUCTION_CA_VERSIONED_ID), 12, commands);

        expect(statsCollectorNames.humanizeUserPrincipal("admin")).andReturn(null);
        replayMocks();

        tester.startPage(new CertificateAuthorityConcurrentModificationPage(exception));
        tester.assertRenderedPage(CertificateAuthorityConcurrentModificationPage.class);

        verifyMocks();
    }

    private List<CommandAuditData> createSingleKeyPairCommandList() {
        List<RoaConfigurationPrefixData> added = Collections.singletonList(new RoaConfigurationPrefixData(Asn.parse("AS123"), IpRange.parse("6.0.0.0/7"), null));
        UpdateRoaConfigurationCommand command = new UpdateRoaConfigurationCommand(new VersionedId(1), added, Collections.emptyList());
        return Collections.singletonList(auditEntry(command));
    }

    private CommandAuditData auditEntry(CertificateAuthorityCommand command) {
        return new CommandAuditData(new DateTime().minusMinutes(10), PRODUCTION_CA_VERSIONED_ID, "admin", command.getCommandType(), command.getCommandGroup(), command.getCommandSummary());
    }
}
