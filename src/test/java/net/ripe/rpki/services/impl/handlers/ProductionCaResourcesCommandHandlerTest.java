package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.KeyPairService;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.ProductionCaResourcesCommand;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ProductionCaResourcesCommandHandlerTest { //extends CertificationDomainTestCase {

    private ProductionCaResourcesCommandHandler subject;
    private ProductionCertificateAuthority prodCa;

    private KeyPairService keyPairService;
    private CertificateRequestCreationService certificateRequestCreationService;
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Before
    public void setUp() {
        prodCa = mock(ProductionCertificateAuthority.class);
        keyPairService = mock(KeyPairService.class);
        certificateRequestCreationService = mock(CertificateRequestCreationService.class);
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);

        subject = new ProductionCaResourcesCommandHandler(certificateAuthorityRepository, keyPairService, certificateRequestCreationService);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        assertSame(ProductionCaResourcesCommand.class, subject.commandType());
    }

    @Test
    public void should_tell_prodCa_to_process() {
        ProductionCaResourcesCommand command = mock(ProductionCaResourcesCommand.class);

        IpResourceSet resources = new IpResourceSet();
        when(command.getResourceClasses()).thenReturn(resources);

        VersionedId versionedId = new VersionedId(1L);
        when(prodCa.getVersionedId()).thenReturn(versionedId);
        when(prodCa.getId()).thenReturn(versionedId.getId());

        when(certificateAuthorityRepository.findHostedCa(prodCa.getId())).thenReturn(prodCa);

        when(command.getCertificateAuthorityVersionedId()).thenReturn(versionedId);

        subject.handle(command);

        verify(prodCa).processCertifiableResources(resources, keyPairService, certificateRequestCreationService);
    }
}
