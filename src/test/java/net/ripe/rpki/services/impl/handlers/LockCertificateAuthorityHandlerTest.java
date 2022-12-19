package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.DeleteCertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.IssueUpdatedManifestAndCrlCommand;
import net.ripe.rpki.server.api.services.command.CommandStatus;
import net.ripe.rpki.util.DBComponent;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class LockCertificateAuthorityHandlerTest {

    private DBComponent dbComponent;
    private LockCertificateAuthorityHandler subject;

    @Before
    public void setUp() {
        dbComponent = mock(DBComponent.class);
        subject = new LockCertificateAuthorityHandler(dbComponent);
    }

    @Test
    public void should_lock_ca_for_any_CertificateAuthorityCommand() {
        subject.handle(new IssueUpdatedManifestAndCrlCommand(new VersionedId(123, 1)), CommandStatus.create());

        verify(dbComponent).lockCertificateAuthorityForUpdate(123L);
        verify(dbComponent).lockCertificateAuthorityForceIncrement(123L);
        verifyNoMoreInteractions(dbComponent);
    }

    @Test
    public void should_lock_parent_ca_for_ActivateHostedCertificateAuthorityCommand() {
        subject.handle(new ActivateHostedCertificateAuthorityCommand(
            new VersionedId(123, 0),
            new X500Principal("CN=test"),
            ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES,
            456
        ), CommandStatus.create());

        verify(dbComponent).lockCertificateAuthorityForUpdate(456L);
        verify(dbComponent).lockCertificateAuthorityForceIncrement(456L);
        verifyNoMoreInteractions(dbComponent);
    }

    @Test
    public void should_lock_child_and_parent_ca_for_any_ChildParentCertificateAuthorityCommand() {
        when(dbComponent.lockCertificateAuthorityForUpdate(123L)).thenReturn(456L);

        subject.handle(new DeleteCertificateAuthorityCommand(
            new VersionedId(123, 0),
            new X500Principal("CN=test")
        ), CommandStatus.create());

        verify(dbComponent).lockCertificateAuthorityForUpdate(123L);
        verify(dbComponent).lockCertificateAuthorityForUpdate(456L);
        verify(dbComponent).lockCertificateAuthorityForceIncrement(123L);
        verify(dbComponent).lockCertificateAuthorityForceIncrement(456L);
        verifyNoMoreInteractions(dbComponent);
    }
}
