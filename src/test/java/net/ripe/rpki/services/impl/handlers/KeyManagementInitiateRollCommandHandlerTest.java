package net.ripe.rpki.services.impl.handlers;

import net.ripe.rpki.commons.crypto.rfc3779.ResourceExtension;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.util.Collections;

import static net.ripe.ipresource.ImmutableResourceSet.ALL_PRIVATE_USE_RESOURCES;
import static net.ripe.rpki.commons.crypto.util.KeyPairFactoryTest.TEST_KEY_PAIR;
import static net.ripe.rpki.domain.TestObjects.ALL_RESOURCES_CA_NAME;
import static net.ripe.rpki.domain.TestObjects.PRODUCTION_CA_NAME;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KeyManagementInitiateRollCommandHandlerTest {

    private static final long CA_ID = 0;
    private static final int THRESHOLD = 24;
    public static final X500Principal MEMBER_CA_NAME = new X500Principal("cn=nl.bluelight");

    private KeyManagementInitiateRollCommandHandler subject;
    private KeyManagementInitiateRollCommand command;
    private CertificateIssuanceRequest request;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;
    @Mock
    private HostedCertificateAuthority memberCA;
    @Mock
    private AllResourcesCertificateAuthority allResourcesCA;
    @Mock
    private ProductionCertificateAuthority productionCA;
    @Mock
    private CertificateRequestCreationService certificateRequestCreationService;
    @Mock
    private ResourceCertificateRepository resourceCertificateRepository;

    @Before
    public void setUp() {
        command = new KeyManagementInitiateRollCommand(new VersionedId(CA_ID, 0), THRESHOLD);
        request = new CertificateIssuanceRequest(ResourceExtension.ofResources(ALL_PRIVATE_USE_RESOURCES), MEMBER_CA_NAME, TEST_KEY_PAIR.getPublic(), new X509CertificateInformationAccessDescriptor[]{});
        subject = new KeyManagementInitiateRollCommandHandler(certificateAuthorityRepository, certificateRequestCreationService, resourceCertificateRepository);

        lenient().when(allResourcesCA.isAllResourcesCa()).thenReturn(true);
        lenient().when(allResourcesCA.isProductionCa()).thenReturn(false);
        lenient().when(allResourcesCA.getName()).thenReturn(ALL_RESOURCES_CA_NAME);

        lenient().when(memberCA.isAllResourcesCa()).thenReturn(false);
        lenient().when(memberCA.isProductionCa()).thenReturn(false);
        lenient().when(memberCA.getParent()).thenReturn(productionCA);
        lenient().when(memberCA.getName()).thenReturn(MEMBER_CA_NAME);

        lenient().when(productionCA.isAllResourcesCa()).thenReturn(false);
        lenient().when(productionCA.isProductionCa()).thenReturn(true);
        lenient().when(productionCA.getParent()).thenReturn(allResourcesCA);
        lenient().when(productionCA.getName()).thenReturn(PRODUCTION_CA_NAME);
    }

    @Test(expected = CommandWithoutEffectException.class)
    public void shouldThrowExceptionIfCommandHadNoEffect() {
        when(certificateAuthorityRepository.findManagedCa(CA_ID)).thenReturn(memberCA);
        when(memberCA.initiateKeyRolls(THRESHOLD, certificateRequestCreationService)).thenReturn(Collections.emptyList());
        subject.handle(command);
    }

    @Test
    public void shouldDelegateRequestsToAllResourcesCa() {
        when(certificateAuthorityRepository.findManagedCa(CA_ID)).thenReturn(allResourcesCA);
        when(allResourcesCA.initiateKeyRolls(THRESHOLD, certificateRequestCreationService)).thenReturn(Collections.singletonList(request));

        subject.handle(command);

        verify(allResourcesCA).setUpStreamCARequestEntity(any(UpStreamCARequestEntity.class));
    }

    @Test
    public void shouldDelegateRequestsToProductionCa() {
        when(certificateAuthorityRepository.findManagedCa(CA_ID)).thenReturn(productionCA);
        when(productionCA.initiateKeyRolls(THRESHOLD, certificateRequestCreationService)).thenReturn(Collections.singletonList(request));

        CertificateIssuanceResponse response = new CertificateIssuanceResponse(mock(X509ResourceCertificate.class), URI.create("rsync://example.com/rpki/cert.cer"));
        when(allResourcesCA.processCertificateIssuanceRequest(productionCA, request, resourceCertificateRepository, Integer.MAX_VALUE)).thenReturn(response);

        subject.handle(command);

        verify(allResourcesCA).processCertificateIssuanceRequest(productionCA, request, resourceCertificateRepository, Integer.MAX_VALUE);
        verify(productionCA).processCertificateIssuanceResponse(response, resourceCertificateRepository);
    }

    @Test
    public void shouldDelegateRequestsToMemberCa() {
        when(certificateAuthorityRepository.findManagedCa(CA_ID)).thenReturn(memberCA);
        when(memberCA.initiateKeyRolls(THRESHOLD, certificateRequestCreationService)).thenReturn(Collections.singletonList(request));

        CertificateIssuanceResponse response = new CertificateIssuanceResponse(mock(X509ResourceCertificate.class), URI.create("rsync://example.com/rpki/cert.cer"));
        when(productionCA.processCertificateIssuanceRequest(memberCA, request, resourceCertificateRepository, Integer.MAX_VALUE)).thenReturn(response);

        subject.handle(command);

        verify(productionCA).processCertificateIssuanceRequest(memberCA, request, resourceCertificateRepository, Integer.MAX_VALUE);
        verify(memberCA).processCertificateIssuanceResponse(response, resourceCertificateRepository);
    }

}
