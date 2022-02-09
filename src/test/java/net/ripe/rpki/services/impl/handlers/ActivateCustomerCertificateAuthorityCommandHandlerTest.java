package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.application.CertificationConfiguration;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.PublishedObjectRepository;
import net.ripe.rpki.domain.ResourceCertificateRepository;
import net.ripe.rpki.domain.archive.KeyPairDeletionService;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.ActivateCustomerCertificateAuthorityCommand;
import net.ripe.rpki.server.api.ports.ResourceLookupService;
import net.ripe.rpki.util.MemoryDBComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ActivateCustomerCertificateAuthorityCommandHandlerTest {

    @Mock private PublishedObjectRepository publishedObjectRepository;
    @Mock private ResourceCertificateRepository resourceCertificateRepository;
    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private CertificationConfiguration certificationConfiguration;
    @Mock
    private KeyPairService keyPairService;
    @Mock
    ResourceLookupService resourceLookupService;
    @Mock
    KeyPairDeletionService keyPairDeletionService;
    @Mock
    CertificateRequestCreationService certificateRequestCreationService;

    @Test
    public void shouldHaveCorrectType() {
        ActivateHostedCustomerCertificateAuthorityCommandHandler subject = new ActivateHostedCustomerCertificateAuthorityCommandHandler(
                certificateAuthorityRepository, certificationConfiguration, keyPairService, resourceLookupService,
            keyPairDeletionService, certificateRequestCreationService, publishedObjectRepository,
                resourceCertificateRepository, new MemoryDBComponent());
        assertSame(ActivateCustomerCertificateAuthorityCommand.class, subject.commandType());
    }

}
