package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.UpdateAllIncomingResourceCertificatesCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UpdateAllIncomingResourceCertificatesCommandHandlerTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private KeyPairService keyPairService;
    @Mock
    private ResourceLookupService resourceLookupService;
    @Mock
    private KeyPairDeletionService keyPairArchingService;
    @Mock
    private CertificateRequestCreationService certificateRequestCreationService;

    @InjectMocks
    private UpdateAllIncomingResourceCertificatesCommandHandler subject;

    @Test
    public void shouldReturnCorrectCommandType() {
        assertSame(UpdateAllIncomingResourceCertificatesCommand.class, subject.commandType());
    }

    @Test
    public void should_skip_ca_without_parent() {
        when(certificateAuthorityRepository.get(any())).thenReturn(new AllResourcesCertificateAuthority(
            TestObjects.ACA_ID,
            TestObjects.ALL_RESOURCES_CA_NAME
        ));

        assertThrows(CommandWithoutEffectException.class, () ->
            subject.handle(new UpdateAllIncomingResourceCertificatesCommand(new VersionedId(TestObjects.ACA_ID, 1), Integer.MAX_VALUE))
        );
    }

}
