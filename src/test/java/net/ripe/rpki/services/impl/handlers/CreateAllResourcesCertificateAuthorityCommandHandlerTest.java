package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

public class CreateAllResourcesCertificateAuthorityCommandHandlerTest {

    private CreateAllResourcesCertificateAuthorityCommandHandler subject;

    private CertificateAuthorityRepository certificateAuthorityRepository;

    private CreateAllResourcesCertificateAuthorityCommand command;


    @Before
    public void setUp() {
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        subject = new CreateAllResourcesCertificateAuthorityCommandHandler(certificateAuthorityRepository);
        command = new CreateAllResourcesCertificateAuthorityCommand(new VersionedId(12), TestObjects.ALL_RESOURCES_CA_NAME);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        Class<? extends CertificateAuthorityCommand> commandType = subject.commandType();

        assertSame(CreateAllResourcesCertificateAuthorityCommand.class, commandType);
    }

    @Test
    public void shouldCreateRootCertificateAuthority() {
        final ArgumentCaptor<AllResourcesCertificateAuthority> ca = ArgumentCaptor.forClass(AllResourcesCertificateAuthority.class);

        subject.handle(command);

        verify(certificateAuthorityRepository).add(ca.capture());
        assertEquals(0, ca.getValue().getVersionedId().getVersion());
        assertEquals(TestObjects.ALL_RESOURCES_CA_NAME, ca.getValue().getName());
    }
}
