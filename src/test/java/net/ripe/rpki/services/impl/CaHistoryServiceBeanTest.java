package net.ripe.rpki.services.impl;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommandGroup;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.CertificateAuthorityHistoryItem;
import net.ripe.rpki.server.api.dto.CommandAuditData;
import net.ripe.rpki.server.api.dto.ProvisioningAuditData;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CaHistoryServiceBeanTest {
    private static final long CA_ID = 456L;
    @Mock
    private CertificateAuthorityViewService certificateAuthorityViewService;
    private CaHistoryServiceBean subject;

    @Before
    public void setUp() {
        subject = new CaHistoryServiceBean(certificateAuthorityViewService);
    }

    @Test
    public void shouldGetResources() throws Exception {
        UUID uuid = UUID.randomUUID();
        CertificateAuthorityData ca = mock(CertificateAuthorityData.class);
        when(ca.getId()).thenReturn(CA_ID);
        when(ca.getUuid()).thenReturn(uuid);

        List<CommandAuditData> commandHistory = Collections.singletonList(new CommandAuditData(
                DateTime.parse("2012-11-12T23:59:21.123Z"),
                new VersionedId(1L), "principal 1", "Some command type",
                CertificateAuthorityCommandGroup.USER, "Some cool command",
                ""));

        List<ProvisioningAuditData> messageHistory = Collections.singletonList(new ProvisioningAuditData(
                DateTime.parse("2013-04-24T11:43:07.789Z"), "principal 2", "Some message"
        ));

        when(certificateAuthorityViewService.findMostRecentCommandsForCa(CA_ID)).thenReturn(commandHistory);
        when(certificateAuthorityViewService.findMostRecentMessagesForCa(uuid)).thenReturn(messageHistory);

        List<CertificateAuthorityHistoryItem> history = subject.getHistoryItems(ca);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getSummary()).isEqualTo("Some message");
        assertThat(history.get(1).getSummary()).isEqualTo("Some cool command");
    }
}