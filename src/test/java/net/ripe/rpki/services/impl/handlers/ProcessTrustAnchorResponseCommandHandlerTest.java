package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.server.api.commands.ProcessTrustAnchorResponseCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;


@RunWith(MockitoJUnitRunner.class)
public class ProcessTrustAnchorResponseCommandHandlerTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    private ProcessTrustAnchorResponseCommandHandler subject;

    @Before
    public void setUp() {
        subject = new ProcessTrustAnchorResponseCommandHandler(certificateAuthorityRepository, null);
    }

    @Test
    public void shouldReturnCorrectType() {
        assertEquals(ProcessTrustAnchorResponseCommand.class, subject.commandType());
    }
}
