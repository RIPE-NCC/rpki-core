package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.IntermediateCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.server.api.commands.CreateIntermediateCertificateAuthorityCommand;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.x500.X500Principal;
import java.util.UUID;

import static net.ripe.rpki.domain.TestObjects.ALL_RESOURCES_CA_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreateIntermediateCertificateAuthorityCommandHandlerTest {

    private CreateIntermediateCertificateAuthorityCommandHandler subject;

    private CertificateAuthorityRepository certificateAuthorityRepository;

    private ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    private CreateIntermediateCertificateAuthorityCommand command;

    @Before
    public void setUp() {
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        childParentCertificateUpdateSaga = mock(ChildParentCertificateUpdateSaga.class);
        subject = new CreateIntermediateCertificateAuthorityCommandHandler(certificateAuthorityRepository, childParentCertificateUpdateSaga);
        command = new CreateIntermediateCertificateAuthorityCommand(new VersionedId(12), new X500Principal("CN=zz.example"), 1L);
    }

    @Test
    public void should_return_correct_command_type() {
        var commandType = subject.commandType();
        assertThat(commandType).isSameAs(CreateIntermediateCertificateAuthorityCommand.class);
    }

    @Test
    public void should_create_intermediate_ca() {
        var ca = ArgumentCaptor.forClass(IntermediateCertificateAuthority.class);

        when(certificateAuthorityRepository.findManagedCa(1L))
            .thenReturn(new ProductionCertificateAuthority(1, ALL_RESOURCES_CA_NAME, UUID.randomUUID(), null));

        subject.handle(command);

        verify(certificateAuthorityRepository).add(ca.capture());
        assertThat(ca.getValue().getName()).isEqualTo(new X500Principal("CN=zz.example"));
        assertThat(ca.getValue().getCertifiedResources()).isEmpty();
        verify(childParentCertificateUpdateSaga).execute(ca.getValue(), Integer.MAX_VALUE);
    }
}
