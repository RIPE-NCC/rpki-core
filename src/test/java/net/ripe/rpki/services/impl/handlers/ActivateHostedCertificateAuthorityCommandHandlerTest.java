package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.ActivateHostedCertificateAuthorityCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertSame;

@RunWith(MockitoJUnitRunner.class)
public class ActivateHostedCertificateAuthorityCommandHandlerTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Test
    public void shouldHaveCorrectType() {
        ActivateHostedCertificateAuthorityCommandHandler subject = new ActivateHostedCertificateAuthorityCommandHandler(
                certificateAuthorityRepository, null);
        assertSame(ActivateHostedCertificateAuthorityCommand.class, subject.commandType());
    }

}
