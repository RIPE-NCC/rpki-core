package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertSame;

@RunWith(MockitoJUnitRunner.class)
public class ActivateCustomerCertificateAuthorityCommandHandlerTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private CertificationConfiguration certificationConfiguration;

    @Test
    public void shouldHaveCorrectType() {
        ActivateHostedCustomerCertificateAuthorityCommandHandler subject = new ActivateHostedCustomerCertificateAuthorityCommandHandler(
                certificateAuthorityRepository, certificationConfiguration, null, null);
        assertSame(ActivateCustomerCertificateAuthorityCommand.class, subject.commandType());
    }

}
