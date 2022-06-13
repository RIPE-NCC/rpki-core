package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CustomerCertificateAuthority;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static net.ripe.ipresource.IpResourceSet.*;
import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MemberKeyRolloverManagementServiceBeanTest {

    private static final CertificateAuthorityData PROD_CA = new HostedCertificateAuthorityData(new VersionedId(0L),
        new X500Principal("CN=zz.example"), UUID.randomUUID(), 1L, ROOT,
        ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

    private static final CertificateAuthorityData MEMBER_CA = new HostedCertificateAuthorityData(new VersionedId(1L),
        new X500Principal("CN=nl.bluelight"), UUID.randomUUID(), PROD_CA.getId(), HOSTED,
        ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

    private MemberKeyRolloverManagementServiceBean subject;

    // Mocks
    private ActiveNodeService propertyEntityService;
    private CertificateAuthorityViewService certificationService;
    private CommandService commandService;
    private CertificationConfiguration certificationConfiguration;


    @Before
    public void setUp() {
        propertyEntityService = mock(ActiveNodeService.class);
        certificationService = mock(CertificateAuthorityViewService.class);
        commandService = mock(CommandService.class);
        certificationConfiguration = mock(CertificationConfiguration.class);

        subject = new MemberKeyRolloverManagementServiceBean(propertyEntityService, certificationConfiguration, certificationService, commandService);
    }

    @Test
    public void shouldReturnIfNoCaFound() throws InterruptedException, ExecutionException {
        when(certificationService.findAllHostedCertificateAuthorities()).thenReturn(Collections.emptyList());

        subject.runService();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldSkipTheProductionCA() {
        when(certificationService.findAllHostedCertificateAuthorities()).thenReturn(Collections.singletonList(PROD_CA));

        subject.runService();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldSendInitialiseKeyCommandToCAs() {
        int maxAge = 365;
        when(certificationService.findAllHostedCasWithCurrentKeyOnlyAndOlderThan(eq(CustomerCertificateAuthority.class), any(), any())).thenReturn(Collections.singletonList(MEMBER_CA));
        when(certificationConfiguration.getAutoKeyRolloverMaxAgeDays()).thenReturn(maxAge);

        subject.runService();

        ArgumentCaptor<KeyManagementInitiateRollCommand> commandCaptor = ArgumentCaptor.forClass(KeyManagementInitiateRollCommand.class);
        Mockito.verify(commandService).execute(commandCaptor.capture());

        KeyManagementInitiateRollCommand command = commandCaptor.getValue();
        assertEquals(MEMBER_CA.getVersionedId(), command.getCertificateAuthorityVersionedId());
        assertEquals(maxAge, command.getMaxAgeDays());
    }


    @Test
    public void shouldKeepProcessingIfProcessingACAFails() throws InterruptedException, ExecutionException {
        when(certificationService.findAllHostedCertificateAuthorities()).thenReturn(Collections.singletonList(MEMBER_CA));

        doThrow(new RuntimeException("test")).when(commandService).execute(isA(KeyManagementActivatePendingKeysCommand.class));

        subject.runService();
    }
}
