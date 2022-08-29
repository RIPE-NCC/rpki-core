package net.ripe.rpki.services.impl.background;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.CertificateAuthorityModificationCommand;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.dto.CertificateAuthorityData;
import net.ripe.rpki.server.api.dto.ManagedCertificateAuthorityData;
import net.ripe.rpki.server.api.dto.NonHostedCertificateAuthorityData;
import net.ripe.rpki.server.api.ports.ResourceCache;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.server.api.services.system.ActiveNodeService;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static net.ripe.ipresource.IpResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.HOSTED;
import static net.ripe.rpki.server.api.dto.CertificateAuthorityType.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyPairActivationManagementServiceBeanTest {

    private static final X500Principal CA_NAME = new X500Principal("CN=nl.bluelight");

    private static final CertificateAuthorityData PROD_CA = new ManagedCertificateAuthorityData(new VersionedId(0L),
        CA_NAME, UUID.randomUUID(), 1L, ROOT,
        ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

    private static final CertificateAuthorityData MEMBER_CA = new ManagedCertificateAuthorityData(new VersionedId(1L),
        CA_NAME, UUID.randomUUID(), PROD_CA.getId(), HOSTED,
        ALL_PRIVATE_USE_RESOURCES, Collections.emptyList());

    private static final CertificateAuthorityData NON_HOSTED_CA = new NonHostedCertificateAuthorityData(new VersionedId(2L),
        CA_NAME, UUID.randomUUID(), PROD_CA.getId(), null, ALL_PRIVATE_USE_RESOURCES, Collections.emptySet());

    @Mock
    private ActiveNodeService propertyEntityService;
    @Mock
    private CertificateAuthorityViewService certificationService;
    @Mock
    private CommandService commandService;
    @Mock
    private ResourceCache resourceCache;
    @Mock
    private CertificationConfiguration configuration;

    private KeyPairActivationManagementServiceBean subject;

    @Before
    public void setUp() {
        subject = new KeyPairActivationManagementServiceBean(propertyEntityService, certificationService, commandService, resourceCache, configuration);
    }

    @Test
    public void shouldReturnIfNoCaFound() {
        when(certificationService.findAllManagedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth()).thenReturn(Collections.emptyList());

        subject.runService();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldThrowExceptionIfResourceCacheIsEmpty() {
        subject.runService();
        verify(resourceCache, times(1)).verifyResourcesArePresent();

        verifyNoInteractions(commandService);
    }

    @Test
    public void shouldSendActivatePendingKeyCommandToProductionCA() {
        when(certificationService.findAllManagedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth()).thenReturn(Collections.singletonList(PROD_CA));
        when(configuration.getStagingPeriod()).thenReturn(Duration.standardHours(24));
        when(commandService.execute(any())).thenReturn(CommandStatus.create());

        subject.runService();

        ArgumentCaptor<KeyManagementActivatePendingKeysCommand> commandCaptor = ArgumentCaptor.forClass(KeyManagementActivatePendingKeysCommand.class);
        verify(commandService).execute(commandCaptor.capture());
        assertEquals(PROD_CA.getVersionedId(), commandCaptor.getValue().getCertificateAuthorityVersionedId());
    }

    @Test
    public void shouldTriggerMemberCertificatesUpdateAfterProductionCAKeyRollover() {
        when(certificationService.findAllManagedCertificateAuthoritiesWithPendingKeyPairsOrderedByDepth()).thenReturn(Collections.singletonList(PROD_CA));
        when(certificationService.findAllChildrenForCa(PROD_CA.getName())).thenReturn(Arrays.asList(MEMBER_CA, NON_HOSTED_CA));
        when(configuration.getStagingPeriod()).thenReturn(Duration.standardHours(24));
        when(commandService.execute(any())).thenReturn(CommandStatus.create());

        subject.runService();

        ArgumentCaptor<CertificateAuthorityModificationCommand> commandCaptor = ArgumentCaptor.forClass(CertificateAuthorityModificationCommand.class);
        verify(commandService, times(3)).execute(commandCaptor.capture());
        List<CertificateAuthorityModificationCommand> capturedCommands = commandCaptor.getAllValues();

        assertTrue(capturedCommands.get(0) instanceof KeyManagementActivatePendingKeysCommand);
        assertEquals(PROD_CA.getVersionedId(), capturedCommands.get(0).getCertificateAuthorityVersionedId());

        assertTrue(capturedCommands.get(1) instanceof UpdateAllIncomingResourceCertificatesCommand);
        assertTrue(capturedCommands.get(2) instanceof UpdateAllIncomingResourceCertificatesCommand);

        assertTrue(capturedCommands.stream().skip(1).anyMatch(c -> MEMBER_CA.getVersionedId().equals(c.getCertificateAuthorityVersionedId())));
        assertTrue(capturedCommands.stream().skip(1).anyMatch(c -> NON_HOSTED_CA.getVersionedId().equals(c.getCertificateAuthorityVersionedId())));
    }

}
