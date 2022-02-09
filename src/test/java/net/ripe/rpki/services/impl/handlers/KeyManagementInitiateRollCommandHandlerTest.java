package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.*;
import net.ripe.rpki.domain.interca.CertificateIssuanceRequest;
import net.ripe.rpki.domain.interca.CertificateIssuanceResponse;
import net.ripe.rpki.domain.rta.UpStreamCARequestEntity;
import net.ripe.rpki.domain.signing.CertificateRequestCreationService;
import net.ripe.rpki.server.api.commands.KeyManagementInitiateRollCommand;
import net.ripe.rpki.server.api.services.command.CommandWithoutEffectException;
import net.ripe.rpki.util.MemoryDBComponent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.Collections;

import static net.ripe.ipresource.IpResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.TEST_KEY_PAIR;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KeyManagementInitiateRollCommandHandlerTest {

    private static final long CA_ID = 0;
    private static final int THRESHOLD = 24;

    private KeyManagementInitiateRollCommandHandler subject;
    private KeyManagementInitiateRollCommand command;
    private CertificateIssuanceRequest request;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private KeyPairService keyPairService;
    @Mock
    private CustomerCertificateAuthority memberCA;
    @Mock
    private AllResourcesCertificateAuthority allResourcesCA;
    @Mock
    private ProductionCertificateAuthority productionCA;
    @Mock
    private CertificateRequestCreationService certificateRequestCreationService;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;

    private  MemoryDBComponent dbComponent = new MemoryDBComponent();

    @Before
    public void setUp() {
        command = new KeyManagementInitiateRollCommand(new VersionedId(CA_ID, 0), THRESHOLD);
        request = new CertificateIssuanceRequest(ALL_PRIVATE_USE_RESOURCES, new X500Principal("cn=nl.bluelight"), TEST_KEY_PAIR.getPublic(), new X509CertificateInformationAccessDescriptor[]{});
        subject = new KeyManagementInitiateRollCommandHandler(certificateAuthorityRepository, keyPairService, certificateRequestCreationService, resourceCertificateRepository, dbComponent);
    }

    @Test(expected = CommandWithoutEffectException.class)
    public void shouldThrowExceptionIfCommandHadNoEffect() {
        when(certificateAuthorityRepository.findHostedCa(CA_ID)).thenReturn(memberCA);
        when(memberCA.initiateKeyRolls(THRESHOLD, keyPairService, certificateRequestCreationService)).thenReturn(Collections.emptyList());
        subject.handle(command);
    }

    @Test
    public void shouldDelegateRequestsToAllResourcesCa() {
        when(certificateAuthorityRepository.findHostedCa(CA_ID)).thenReturn(allResourcesCA);
        when(allResourcesCA.initiateKeyRolls(THRESHOLD, keyPairService, certificateRequestCreationService)).thenReturn(Collections.singletonList(request));
        when(allResourcesCA.isAllResourcesCa()).thenReturn(true);

        subject.handle(command);

        verify(allResourcesCA).setUpStreamCARequestEntity(any(UpStreamCARequestEntity.class));
    }

    @Test
    public void shouldDelegateRequestsToProductionCa() {
        when(certificateAuthorityRepository.findHostedCa(CA_ID)).thenReturn(productionCA);
        when(productionCA.initiateKeyRolls(THRESHOLD, keyPairService, certificateRequestCreationService)).thenReturn(Collections.singletonList(request));
        when(productionCA.getParent()).thenReturn(allResourcesCA);

        CertificateIssuanceResponse response = new CertificateIssuanceResponse(mock(X509ResourceCertificate.class), URI.create("rsync://example.com/rpki/cert.cer"));
        when(allResourcesCA.processCertificateIssuanceRequest(request, resourceCertificateRepository, dbComponent)).thenReturn(response);

        subject.handle(command);

        verify(allResourcesCA).processCertificateIssuanceRequest(request, resourceCertificateRepository, dbComponent);
        verify(productionCA).processCertificateIssuanceResponse(response, resourceCertificateRepository);
    }

    @Test
    public void shouldDelegateRequestsToMemberCa() {
        when(certificateAuthorityRepository.findHostedCa(CA_ID)).thenReturn(memberCA);
        when(memberCA.initiateKeyRolls(THRESHOLD, keyPairService, certificateRequestCreationService)).thenReturn(Collections.singletonList(request));
        when(memberCA.getParent()).thenReturn(productionCA);

        CertificateIssuanceResponse response = new CertificateIssuanceResponse(mock(X509ResourceCertificate.class), URI.create("rsync://example.com/rpki/cert.cer"));
        when(productionCA.processCertificateIssuanceRequest(request, resourceCertificateRepository, dbComponent)).thenReturn(response);

        subject.handle(command);

        verify(productionCA).processCertificateIssuanceRequest(request, resourceCertificateRepository, dbComponent);
        verify(memberCA).processCertificateIssuanceResponse(response, resourceCertificateRepository);
    }
}
