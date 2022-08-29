package net.ripe.rpki.services.impl.handlers;

import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ProductionCertificateAuthority;
import net.ripe.rpki.domain.ResourceClassListQuery;
import net.ripe.rpki.domain.ResourceClassListResponse;
import net.ripe.rpki.server.api.commands.ActivateNonHostedCertificateAuthorityCommand;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActivateNonHostedCustomerCertificateAuthorityCommandHandlerTest {
    private ActivateNonHostedCertificateAuthorityCommandHandler subject;
    private CertificateAuthorityRepository certificateAuthorityRepository;

    private static final X500Principal CUSTOMER_CA_NAME = new X500Principal("CN=Test Customer CA");

    @Before
    public void setUp() {
        certificateAuthorityRepository = mock(CertificateAuthorityRepository.class);
        subject = new ActivateNonHostedCertificateAuthorityCommandHandler(certificateAuthorityRepository);
    }

    @Test
    public void shouldReturnCorrectCommandType() {
        assertSame(ActivateNonHostedCertificateAuthorityCommand.class, subject.commandType());
    }

    @Test
    public void shouldCreateNonHostedCA() {
        // given
        ProvisioningIdentityCertificate certificate = mock(ProvisioningIdentityCertificate.class);
        when(certificate.getEncoded()).thenReturn(new byte[] { 1,2,3});

        ProductionCertificateAuthority parent = mock(ProductionCertificateAuthority.class);
        when(parent.processResourceClassListQuery(any(ResourceClassListQuery.class))).thenReturn(new ResourceClassListResponse(
            IpResourceSet.ALL_PRIVATE_USE_RESOURCES
        ));

        when(certificateAuthorityRepository.findManagedCa(1L)).thenReturn(parent);

        ActivateNonHostedCertificateAuthorityCommand command = new ActivateNonHostedCertificateAuthorityCommand(new VersionedId(1),
            CUSTOMER_CA_NAME, IpResourceSet.ALL_PRIVATE_USE_RESOURCES, certificate, 1);

        // when
        subject.handle(command);

        // then
        verify(certificateAuthorityRepository).add(isA(NonHostedCertificateAuthority.class));
    }
}
