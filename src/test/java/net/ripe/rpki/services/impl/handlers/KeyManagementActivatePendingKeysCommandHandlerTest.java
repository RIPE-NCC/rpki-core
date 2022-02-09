package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.KeyManagementActivatePendingKeysCommand;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyManagementActivatePendingKeysCommandHandlerTest {

    private static final Duration RFC_STAGING_PERIOD = Duration.standardDays(24);

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private ProductionCertificateAuthority ca;
    private long caId = 42L;

    private KeyManagementActivatePendingKeysCommandHandler subject;

    @Before
    public void setUp() {
        subject = new KeyManagementActivatePendingKeysCommandHandler(certificateAuthorityRepository);
    }


    @Test
    public void should_activate_keys_for_ProductionCertificateAuthority() {
        // Expect that the CA is looked up
        when(ca.getVersionedId()).thenReturn(new VersionedId(caId));
        when(certificateAuthorityRepository.findHostedCa(caId)).thenReturn(ca);

        // Expect that the CA is asked to activate pending keys, and return success
        when(ca.activatePendingKeys(isA(Duration.class))).thenReturn(true);

        // Send the actual command
        KeyManagementActivatePendingKeysCommand command = KeyManagementActivatePendingKeysCommand.plannedActivationCommand(ca.getVersionedId(), RFC_STAGING_PERIOD);
        subject.handle(command);

        // Verify some mocks explicitly (expectations above)
        verify(ca).activatePendingKeys(command.getMinStagingTime());
    }

    @Test
    public void should_activate_keys_for_CustomerCertificateAuthority() {
        // Expect that the CA is looked up
        when(ca.getVersionedId()).thenReturn(new VersionedId(caId));
        when(certificateAuthorityRepository.findHostedCa(caId)).thenReturn(ca);

        // Expect that the CA is asked to activate pending keys, and return success
        when(ca.activatePendingKeys(isA(Duration.class))).thenReturn(true);

        // Send the actual command
        KeyManagementActivatePendingKeysCommand command = KeyManagementActivatePendingKeysCommand.plannedActivationCommand(ca.getVersionedId(), RFC_STAGING_PERIOD);
        subject.handle(command);

        // Verify some mocks explicitly (expectations above)
        verify(ca).activatePendingKeys(command.getMinStagingTime());
    }

    @Test(expected = CommandWithoutEffectException.class)
    public void should_throw_CommandWithoutEffectException_when_command_has_no_effect() {
        // Expect that the CA is looked up
        when(ca.getVersionedId()).thenReturn(new VersionedId(caId));
        when(certificateAuthorityRepository.findHostedCa(caId)).thenReturn(ca);

        // Expect that the CA is asked to activate pending keys, and return success
        when(ca.activatePendingKeys(isA(Duration.class))).thenThrow(new CommandWithoutEffectException(""));

        // Send the actual command
        KeyManagementActivatePendingKeysCommand command = KeyManagementActivatePendingKeysCommand.plannedActivationCommand(ca.getVersionedId(), RFC_STAGING_PERIOD);
        subject.handle(command);
    }
}
