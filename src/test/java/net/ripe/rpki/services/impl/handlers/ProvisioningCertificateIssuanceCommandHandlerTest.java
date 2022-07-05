package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.provisioning.payload.PayloadMessageType;
import net.ripe.rpki.commons.provisioning.x509.ProvisioningIdentityCertificateBuilderTest;
import net.ripe.rpki.domain.AllResourcesCertificateAuthority;
import net.ripe.rpki.domain.CertificateAuthorityRepository;
import net.ripe.rpki.domain.NonHostedCertificateAuthority;
import net.ripe.rpki.domain.ParentCertificateAuthority;
import net.ripe.rpki.domain.RequestedResourceSets;
import net.ripe.rpki.domain.TestObjects;
import net.ripe.rpki.server.api.commands.ProvisioningCertificateIssuanceCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

import static net.ripe.rpki.domain.CertificationDomainTestCase.ALL_RESOURCES_CA_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProvisioningCertificateIssuanceCommandHandlerTest {

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private ChildParentCertificateUpdateSaga childParentCertificateUpdateSaga;

    private NonHostedCertificateAuthority nonHostedCertificateAuthority;

    private PublicKey publicKey = TestObjects.TEST_KEY_PAIR_2.getPublicKey();
    private RequestedResourceSets requestedResourceSets = new RequestedResourceSets();
    private List<X509CertificateInformationAccessDescriptor> sia = Collections.emptyList();

    private ProvisioningCertificateIssuanceCommandHandler subject;

    @Before
    public void setUp() {
        ParentCertificateAuthority parent = new AllResourcesCertificateAuthority(1L, ALL_RESOURCES_CA_NAME);
        nonHostedCertificateAuthority = new NonHostedCertificateAuthority(12L, new X500Principal("CN=101"), ProvisioningIdentityCertificateBuilderTest.TEST_IDENTITY_CERT, parent);

        subject = new ProvisioningCertificateIssuanceCommandHandler(certificateAuthorityRepository, childParentCertificateUpdateSaga);
    }

    @Test
    public void should_add_public_key_and_issue_certificate() {
        when(certificateAuthorityRepository.findNonHostedCa(12L)).thenReturn(nonHostedCertificateAuthority);
        when(childParentCertificateUpdateSaga.execute(any(), anyInt())).thenReturn(true);

        subject.handle(new ProvisioningCertificateIssuanceCommand(
            nonHostedCertificateAuthority.getVersionedId(),
            publicKey,
            requestedResourceSets,
            sia
        ));

        assertThat(nonHostedCertificateAuthority.getPublicKeys()).hasSize(1).allSatisfy(pke -> {
            assertThat(pke.getLatestProvisioningRequestType()).isEqualTo(PayloadMessageType.issue);
            assertThat(pke.getRequestedResourceSets()).isSameAs(requestedResourceSets);
            assertThat(pke.getRequestedSia()).isEqualTo(sia);
        });
        verify(childParentCertificateUpdateSaga).execute(nonHostedCertificateAuthority, NonHostedCertificateAuthority.INCOMING_RESOURCE_CERTIFICATES_PER_PUBLIC_KEY_LIMIT);
    }
}
