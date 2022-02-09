package net.ripe.rpki.services.impl.background;


import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

public class KeyPairRevocationManagementServiceBeanTest {

    private CertificateAuthorityViewService certificationService;
    private CommandService commandService;

    private KeyPairRevocationManagementServiceBean subject;

    @Before
    public void setUp() {
        ActiveNodeService propertyEntityService = mock(ActiveNodeService.class);
        certificationService = mock(CertificateAuthorityViewService.class);
        commandService = mock(CommandService.class);

        subject = new KeyPairRevocationManagementServiceBean(propertyEntityService, certificationService, commandService);
    }

    @Test
    public void shouldReturnIfNoCaFound() {
        given(certificationService.findAllHostedCertificateAuthorities()).willReturn(Collections.emptyList());

        subject.runService();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldDispatchKeyManagementRevokeOldKeysCommandForCa() {
        ArgumentCaptor<KeyManagementRevokeOldKeysCommand> captor = ArgumentCaptor.forClass(KeyManagementRevokeOldKeysCommand.class);
        VersionedId expectedVersionedId = VersionedId.parse("1:1");

        HostedCertificateAuthorityData ca = mock(HostedCertificateAuthorityData.class);
        given(ca.getVersionedId()).willReturn(expectedVersionedId);
        given(certificationService.findAllHostedCertificateAuthorities()).willReturn(Collections.singletonList(ca));

        subject.runService();

        verify(commandService).execute(captor.capture());
        assertEquals(expectedVersionedId, captor.getValue().getCertificateAuthorityVersionedId());
    }
}
