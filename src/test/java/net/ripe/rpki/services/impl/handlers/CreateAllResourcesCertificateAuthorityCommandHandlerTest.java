package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CreateAllResourcesCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.x500.X500Principal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

public class CreateAllResourcesCertificateAuthorityCommandHandlerTest {

	private CreateAllResourcesCertificateAuthorityCommandHandler subject;

	private CertificateAuthorityRepository certificateAuthorityRepository;

    private RepositoryConfiguration repositoryConfiguration;
	private CertificationConfiguration certificationConfiguration;

	private CreateAllResourcesCertificateAuthorityCommand command;


	@Before
    public void setUp() {
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        certificationConfiguration = mock(CertificationConfiguration.class);
        repositoryConfiguration = mock(RepositoryConfiguration.class);
        subject = new CreateAllResourcesCertificateAuthorityCommandHandler(certificateAuthorityRepository, repositoryConfiguration, certificationConfiguration);
        command = new CreateAllResourcesCertificateAuthorityCommand(new VersionedId(12));
    }

	@Test
	public void shouldReturnCorrectCommandType() {
		Class<? extends CertificateAuthorityCommand> commandType = subject.commandType();

		assertSame(CreateAllResourcesCertificateAuthorityCommand.class, commandType);
	}

    @Test
    public void shouldCreateRootCertificateAuthority() {
        final ArgumentCaptor<AllResourcesCertificateAuthority> ca = ArgumentCaptor.forClass(AllResourcesCertificateAuthority.class);
        when(certificationConfiguration.getMaxSerialIncrement()).thenReturn(9);

        final X500Principal prodCaName = new X500Principal("CN=All Resources CA");

        when(repositoryConfiguration.getAllResourcesCaPrincipal()).thenReturn(prodCaName);

        subject.handle(command);

        verify(certificateAuthorityRepository).add(ca.capture());
        assertEquals(0, ca.getValue().getVersionedId().getVersion());
        assertEquals(prodCaName, ca.getValue().getName());
    }
}
