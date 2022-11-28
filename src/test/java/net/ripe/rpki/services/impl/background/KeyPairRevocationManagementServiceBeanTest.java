package net.ripe.rpki.services.impl.background;


import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.core.services.background.BackgroundTaskRunner;
import net.ripe.rpki.server.api.commands.KeyManagementRevokeOldKeysCommand;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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
        ActiveNodeService activeNodeService = mock(ActiveNodeService.class);
        certificationService = mock(CertificateAuthorityViewService.class);
        commandService = mock(CommandService.class);

        subject = new KeyPairRevocationManagementServiceBean(new BackgroundTaskRunner(activeNodeService, new SimpleMeterRegistry()), certificationService, commandService);

        when(activeNodeService.isActiveNode()).thenReturn(true);
    }

    @Test
    public void shouldReturnIfNoCaFound() {
        given(certificationService.findManagedCasEligibleForKeyRevocation()).willReturn(Collections.emptyList());

        subject.execute();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldDispatchKeyManagementRevokeOldKeysCommandForCa() {
        ArgumentCaptor<KeyManagementRevokeOldKeysCommand> captor = ArgumentCaptor.forClass(KeyManagementRevokeOldKeysCommand.class);
        VersionedId expectedVersionedId = VersionedId.parse("1:1");

        ManagedCertificateAuthorityData ca = mock(ManagedCertificateAuthorityData.class);
        given(ca.getVersionedId()).willReturn(expectedVersionedId);
        given(certificationService.findManagedCasEligibleForKeyRevocation()).willReturn(Collections.singletonList(ca));

        subject.execute();

        verify(commandService).execute(captor.capture());
        assertEquals(expectedVersionedId, captor.getValue().getCertificateAuthorityVersionedId());
    }
}
