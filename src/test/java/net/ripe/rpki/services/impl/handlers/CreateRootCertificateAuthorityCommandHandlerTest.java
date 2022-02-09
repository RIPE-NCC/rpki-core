package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.CertificateAuthorityCommand;
import net.ripe.rpki.server.api.commands.CreateRootCertificateAuthorityCommand;
import net.ripe.rpki.server.api.configuration.RepositoryConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.x500.X500Principal;

import static net.ripe.rpki.domain.CertificationDomainTestCase.ALL_RESOURCES_CA_NAME;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CreateRootCertificateAuthorityCommandHandlerTest {

	private static final X500Principal CA_NAME = new X500Principal("CN=Test Root CA");

	private CreateRootCertificateAuthorityCommandHandler subject;

	private CertificateAuthorityRepository certificateAuthorityRepository;

	private RepositoryConfiguration repositoryConfiguration;
	private CertificationConfiguration certificationConfiguration;

	private CreateRootCertificateAuthorityCommand command;


	@Before
    public void setUp() {
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        certificationConfiguration = mock(CertificationConfiguration.class);
        repositoryConfiguration = mock(RepositoryConfiguration.class);
        subject = new CreateRootCertificateAuthorityCommandHandler(certificateAuthorityRepository, repositoryConfiguration, certificationConfiguration);
        command = new CreateRootCertificateAuthorityCommand(new VersionedId(12));
    }

	@Test
	public void shouldReturnCorrectCommandType() {
		Class<? extends CertificateAuthorityCommand> commandType = subject.commandType();

		assertSame(CreateRootCertificateAuthorityCommand.class, commandType);
	}

    @Test
    public void shouldCreateRootCertificateAuthority() {
        ArgumentCaptor<ProductionCertificateAuthority> ca = ArgumentCaptor.forClass(ProductionCertificateAuthority.class);
        when(certificationConfiguration.getMaxSerialIncrement()).thenReturn(9);

        X500Principal prodCaName = new X500Principal("CN=production");

        when(repositoryConfiguration.getAllResourcesCaPrincipal()).thenReturn(ALL_RESOURCES_CA_NAME);
        when(certificateAuthorityRepository.findByTypeAndName(AllResourcesCertificateAuthority.class, ALL_RESOURCES_CA_NAME))
            .thenReturn(new AllResourcesCertificateAuthority(1, ALL_RESOURCES_CA_NAME, 1));
        when(repositoryConfiguration.getProductionCaPrincipal()).thenReturn(prodCaName);

        subject.handle(command);

        verify(certificateAuthorityRepository).add(ca.capture());
        assertEquals(0, ca.getValue().getVersionedId().getVersion());
        assertEquals(prodCaName, ca.getValue().getName());
        assertEquals(new IpResourceSet(), ca.getValue().getCertifiedResources());
    }
}
